package com.pharmacy.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pharmacy.config.AppProperties;
import com.pharmacy.dto.ReportTable;
import com.pharmacy.entity.*;
import com.pharmacy.util.Fmt;
import com.pharmacy.util.L10n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A4 invoices and report exports as PDF, with Arabic (RTL + letter shaping) support. */
@Service
public class PdfService {
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);
    private static final Color HEADER_BG = new Color(33, 37, 41);
    private static final Color LIGHT_BG = new Color(241, 243, 245);

    private final AppProperties props;
    private final SettingsService settings;
    private final BarcodeService barcodes;
    private final FileStorageService files;
    private final Fmt fmt;
    private BaseFont baseFont;

    public PdfService(AppProperties props, SettingsService settings, BarcodeService barcodes,
                      FileStorageService files, Fmt fmt) {
        this.props = props;
        this.settings = settings;
        this.barcodes = barcodes;
        this.files = files;
        this.fmt = fmt;
    }

    // ------------------------------------------------------------------ invoices

    private record Line(String name, String qty, String price, String discount, String total) {}

    public byte[] salePdf(Sale s) {
        List<Line> lines = new ArrayList<>();
        for (SaleItem i : s.getItems()) {
            lines.add(new Line(i.getProduct().getName(), fmt.qty(i.getQty()) + " " + fmt.unitName(i.getProduct(), i.getSellUnit()), fmt.money(i.getUnitPrice()),
                    fmt.money(i.getDiscount()) + fmt.pct(i.getDiscountPercent()), fmt.money(i.getLineTotal())));
        }
        List<String[]> totals = new ArrayList<>();
        totals.add(new String[]{fmt.msg("col.subtotal"), fmt.money(s.getSubtotal())});
        if (s.getDiscount().signum() > 0) totals.add(new String[]{fmt.msg("col.discount") + fmt.pct(s.getDiscountPercent()), fmt.money(s.getDiscount())});
        if (s.getTaxAmount().signum() > 0)
            totals.add(new String[]{fmt.msg("col.tax") + " (" + s.getTaxRate().stripTrailingZeros().toPlainString() + "%)", fmt.money(s.getTaxAmount())});
        totals.add(new String[]{fmt.msg("col.total"), fmt.money(s.getTotal())});
        totals.add(new String[]{fmt.msg("col.paid"), fmt.money(s.getPaid())});
        if (s.getRemaining().signum() > 0) totals.add(new String[]{fmt.msg("col.remaining"), fmt.money(s.getRemaining())});
        if (s.getReturnedAmount().signum() > 0) totals.add(new String[]{fmt.msg("col.returns"), fmt.money(s.getReturnedAmount())});

        String party = s.getCustomer() == null ? fmt.msg("pos.walkIn") : s.getCustomer().getName();
        String phone = s.getCustomer() == null ? null : s.getCustomer().getPhone();
        return invoice(fmt.msg("invoice.taxInvoice"), s.getInvoiceNo(), fmt.dateTime(s.getDate()),
                fmt.msg("col.customer"), party, phone,
                fmt.msg("col.payment") + ": " + fmt.msg("payment." + s.getPaymentMethod().name()),
                s.getUser() == null ? "" : s.getUser().getFullName(), lines, true, totals, s.getNotes());
    }

    public byte[] purchasePdf(Purchase p) {
        List<Line> lines = new ArrayList<>();
        for (PurchaseItem i : p.getItems()) {
            lines.add(new Line(i.getProduct().getName(), fmt.qty(i.getQty()) + (i.getBonusQty().signum() > 0 ? " + " + fmt.qty(i.getBonusQty()) : ""), fmt.money(i.getUnitCost()), "", fmt.money(i.getLineTotal())));
        }
        List<String[]> totals = new ArrayList<>();
        totals.add(new String[]{fmt.msg("col.subtotal"), fmt.money(p.getSubtotal())});
        if (p.getDiscount().signum() > 0) totals.add(new String[]{fmt.msg("col.discount") + fmt.pct(p.getDiscountPercent()), fmt.money(p.getDiscount())});
        if (p.getTaxAmount().signum() > 0) totals.add(new String[]{fmt.msg("col.tax"), fmt.money(p.getTaxAmount())});
        totals.add(new String[]{fmt.msg("col.total"), fmt.money(p.getTotal())});
        totals.add(new String[]{fmt.msg("col.paid"), fmt.money(p.getPaid())});
        if (p.getRemaining().signum() > 0) totals.add(new String[]{fmt.msg("col.remaining"), fmt.money(p.getRemaining())});
        return invoice(fmt.msg("purchase.invoice"), p.getInvoiceNo(), fmt.dateTime(p.getDate()),
                fmt.msg("col.supplier"), p.getSupplier().getName(), p.getSupplier().getPhone(),
                p.getSupplierInvoiceNo() == null ? "" : fmt.msg("purchase.supplierInvoiceNo") + ": " + p.getSupplierInvoiceNo(),
                p.getUser() == null ? "" : p.getUser().getFullName(), lines, false, totals, p.getNotes());
    }

    private byte[] invoice(String title, String number, String date, String partyLabel, String party, String partyPhone,
                           String extra, String userName, List<Line> lines, boolean withDiscount,
                           List<String[]> totals, String notes) {
        StoreSettings st = settings.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 44);   // larger bottom margin for the vendor footer
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(vendorFooter());
            doc.open();
            Font normal = font(10, Font.NORMAL), bold = font(10, Font.BOLD), big = font(16, Font.BOLD), small = font(8, Font.NORMAL);

            // Header: store info | logo
            PdfPTable header = table(new float[]{3, 1});
            PdfPCell info = cell("", normal, Element.ALIGN_LEFT);
            info.setBorder(Rectangle.NO_BORDER);
            info.addElement(para(st.getStoreName(), big));
            if (notBlank(st.getAddress())) info.addElement(para(st.getAddress(), normal));
            if (notBlank(st.getPhones())) info.addElement(para(fmt.msg("settings.phones") + ": " + st.getPhones(), normal));
            if (notBlank(st.getTaxNumber())) info.addElement(para(fmt.msg("settings.taxNumber") + ": " + st.getTaxNumber(), normal));
            if (notBlank(st.getCommercialRegister())) info.addElement(para(fmt.msg("settings.commercialRegister") + ": " + st.getCommercialRegister(), normal));
            header.addCell(info);
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            Path logo = files.resolve(st.getLogoPath());
            if (logo != null && !logo.toString().endsWith(".svg")) {
                Image img = Image.getInstance(Files.readAllBytes(logo));
                img.scaleToFit(90, 70);
                logoCell.addElement(img);
            }
            header.addCell(logoCell);
            doc.add(header);

            // Title + barcode of the invoice number
            PdfPTable titleRow = table(new float[]{2, 1});
            PdfPCell t = cell(title, font(14, Font.BOLD), Element.ALIGN_LEFT);
            t.setBorder(Rectangle.NO_BORDER);
            t.setPaddingTop(12);
            titleRow.addCell(t);
            Image bc = Image.getInstance(barcodes.png(number, 300, 60));
            bc.scaleToFit(150, 30);
            PdfPCell bcCell = new PdfPCell(bc);
            bcCell.setBorder(Rectangle.NO_BORDER);
            bcCell.setPaddingTop(10);
            bcCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            titleRow.addCell(bcCell);
            doc.add(titleRow);

            // Meta: number, date, party...
            PdfPTable meta = table(new float[]{1, 2, 1, 2});
            meta.setSpacingBefore(6);
            addMeta(meta, fmt.msg("col.invoiceNo"), number, bold, normal);
            addMeta(meta, fmt.msg("col.date"), date, bold, normal);
            addMeta(meta, partyLabel, party + (notBlank(partyPhone) ? " - " + partyPhone : ""), bold, normal);
            addMeta(meta, fmt.msg("col.user"), userName == null ? "" : userName, bold, normal);
            if (notBlank(extra)) addMeta(meta, "", extra, bold, normal);
            meta.completeRow();
            doc.add(meta);

            // Items
            float[] widths = withDiscount ? new float[]{0.5f, 4, 1, 1.3f, 1.2f, 1.5f} : new float[]{0.5f, 4, 1, 1.3f, 1.5f};
            PdfPTable items = table(widths);
            items.setSpacingBefore(10);
            items.setHeaderRows(1);
            List<String> heads = new ArrayList<>(List.of("#", fmt.msg("col.product"), fmt.msg("col.qty"), fmt.msg("col.price")));
            if (withDiscount) heads.add(fmt.msg("col.discount"));
            heads.add(fmt.msg("col.total"));
            for (String h : heads) items.addCell(headCell(h));
            int n = 1;
            for (Line l : lines) {
                items.addCell(cell(String.valueOf(n++), normal, Element.ALIGN_CENTER));
                items.addCell(cell(l.name(), normal, Element.ALIGN_LEFT));
                items.addCell(cell(l.qty(), normal, Element.ALIGN_CENTER));
                items.addCell(cell(l.price(), normal, Element.ALIGN_CENTER));
                if (withDiscount) items.addCell(cell(l.discount(), normal, Element.ALIGN_CENTER));
                items.addCell(cell(l.total(), normal, Element.ALIGN_CENTER));
            }
            doc.add(items);

            // Totals
            PdfPTable tot = table(new float[]{3, 1.4f, 1.2f});
            tot.setSpacingBefore(8);
            for (String[] row : totals) {
                PdfPCell blank = new PdfPCell();
                blank.setBorder(Rectangle.NO_BORDER);
                tot.addCell(blank);
                PdfPCell label = cell(row[0], bold, Element.ALIGN_LEFT);
                label.setBackgroundColor(LIGHT_BG);
                tot.addCell(label);
                tot.addCell(cell(row[1], bold, Element.ALIGN_CENTER));
            }
            doc.add(tot);

            if (notBlank(notes)) {
                // Inside a table cell: only cells get RTL + Arabic letter joining in OpenPDF.
                PdfPTable notesTable = table(new float[]{1});
                notesTable.setSpacingBefore(6);
                PdfPCell notesCell = cell(fmt.msg("col.notes") + ": " + notes, normal, Element.ALIGN_LEFT);
                notesCell.setBorder(Rectangle.NO_BORDER);
                notesTable.addCell(notesCell);
                doc.add(notesTable);
            }
            if (notBlank(st.getInvoiceFooter())) {
                PdfPTable footer = centered(st.getInvoiceFooter(), small);
                footer.setSpacingBefore(20);
                doc.add(footer);
            }
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
        return out.toByteArray();
    }

    private void addMeta(PdfPTable t, String label, String value, Font bold, Font normal) {
        PdfPCell l = cell(label, bold, Element.ALIGN_LEFT);
        l.setBackgroundColor(LIGHT_BG);
        t.addCell(l);
        t.addCell(cell(value, normal, Element.ALIGN_LEFT));
    }

    // ------------------------------------------------------------------ reports

    public byte[] reportPdf(ReportTable report, LocalDate from, LocalDate to) {
        StoreSettings st = settings.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle size = report.getHeaderKeys().size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(size, 28, 28, 28, 40);   // larger bottom margin for the vendor footer
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(vendorFooter());
            doc.open();
            Font normal = font(9, Font.NORMAL), bold = font(9, Font.BOLD);
            doc.add(centered(st.getStoreName(), font(14, Font.BOLD)));
            doc.add(centered(fmt.msg(report.getTitleKey()), font(12, Font.BOLD)));
            if (from != null) doc.add(centered(fmt.msg("report.period", fmt.date(from), fmt.date(to)), normal));

            if (!report.getSummary().isEmpty()) {
                PdfPTable sum = table(new float[]{2, 1});
                sum.setWidthPercentage(60);
                sum.setSpacingBefore(10);
                for (ReportTable.Summary s : report.getSummary()) {
                    PdfPCell l = cell(fmt.msg(s.labelKey()), bold, Element.ALIGN_LEFT);
                    l.setBackgroundColor(LIGHT_BG);
                    sum.addCell(l);
                    sum.addCell(cell(fmt.cell(s.value()), s.highlight() ? font(10, Font.BOLD) : normal, Element.ALIGN_CENTER));
                }
                doc.add(sum);
            }

            int cols = report.getHeaderKeys().size();
            float[] w = new float[cols];
            for (int i = 0; i < cols; i++) w[i] = 1;
            PdfPTable t = table(w);
            t.setSpacingBefore(12);
            t.setHeaderRows(1);
            for (String h : report.getHeaderKeys()) t.addCell(headCell(fmt.msg(h)));
            for (List<Object> row : report.getRows()) {
                for (Object c : row) t.addCell(cell(fmt.cell(c), normal, fmt.isNum(c) ? Element.ALIGN_CENTER : Element.ALIGN_LEFT));
            }
            if (report.getTotals() != null) {
                boolean first = true;
                for (Object c : report.getTotals()) {
                    PdfPCell pc = cell(first ? fmt.msg(String.valueOf(c)) : fmt.cell(c), bold, Element.ALIGN_CENTER);
                    pc.setBackgroundColor(LIGHT_BG);
                    t.addCell(pc);
                    first = false;
                }
            }
            if (report.getRows().isEmpty()) {
                PdfPCell empty = cell(fmt.msg("common.noData"), normal, Element.ALIGN_CENTER);
                empty.setColspan(cols);
                t.addCell(empty);
            }
            doc.add(t);
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ helpers

    /** Bottom of every report page: "Powered by <vendor> | <phone>" and the page number. */
    private PdfPageEventHelper vendorFooter() {
        String text = fmt.msg("footer.poweredByLine", props.vendor().name(), props.vendor().phone());
        Font f = font(7, Font.NORMAL, new Color(108, 117, 125));
        int dir = L10n.isArabic() ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
        return new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                PdfContentByte cb = writer.getDirectContent();
                float y = document.bottom() - 14;
                cb.setColorStroke(new Color(206, 212, 218));
                cb.setLineWidth(0.5f);
                cb.moveTo(document.left(), y + 9);
                cb.lineTo(document.right(), y + 9);
                cb.stroke();
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(text, f),
                        (document.left() + document.right()) / 2, y, 0, dir, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(String.valueOf(writer.getPageNumber()), f),
                        document.left(), y, 0, PdfWriter.RUN_DIRECTION_LTR, 0);
            }
        };
    }

    /** Widths are given in logical (reading) order; in RTL tables OpenPDF applies them visually left-to-right, so reverse them. */
    private PdfPTable table(float[] widths) {
        float[] w = widths.clone();
        if (L10n.isArabic()) {
            for (int i = 0, j = w.length - 1; i < j; i++, j--) {
                float tmp = w[i];
                w[i] = w[j];
                w[j] = tmp;
            }
        }
        PdfPTable t = new PdfPTable(w);
        t.setWidthPercentage(100);
        if (L10n.isArabic()) t.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        return t;
    }

    private PdfPCell headCell(String text) {
        PdfPCell c = cell(text, font(9, Font.BOLD, Color.WHITE), Element.ALIGN_CENTER);
        c.setBackgroundColor(HEADER_BG);
        return c;
    }

    /** Alignment is given for LTR; OpenPDF mirrors LEFT/RIGHT itself when the run direction is RTL. */
    private PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(4);
        c.setRunDirection(runDirection(text));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderColor(new Color(206, 212, 218));
        return c;
    }

    private Paragraph para(String text, Font font) {
        Paragraph p = new Paragraph(text == null ? "" : text, font);
        p.setAlignment(Element.ALIGN_LEFT);   // start side: mirrored to the right inside RTL cells
        return p;
    }

    /** Centered line; wrapped in a table so Arabic shaping/RTL is applied. */
    private PdfPTable centered(String text, Font font) {
        PdfPTable t = table(new float[]{1});
        PdfPCell c = cell(text, font, Element.ALIGN_CENTER);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
        return t;
    }

    private int runDirection(String text) {
        if (L10n.isArabic() || containsArabic(text)) return PdfWriter.RUN_DIRECTION_RTL;
        return PdfWriter.RUN_DIRECTION_LTR;
    }

    private static boolean containsArabic(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(s.charAt(i));
            if (b == Character.UnicodeBlock.ARABIC || b == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                    || b == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B) return true;
        }
        return false;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private Font font(float size, int style) {
        return font(size, style, Color.BLACK);
    }

    private Font font(float size, int style, Color color) {
        baseFont();
        // With the bundled font use its real bold face; a configured single font gets simulated bold.
        if ((style & Font.BOLD) != 0 && boldFont != null) {
            return new Font(boldFont, size, style & ~Font.BOLD, color);
        }
        return new Font(baseFont, size, style, color);
    }

    /** DejaVu Sans (free, includes Arabic) shipped inside the app, from the jasperreports-fonts jar. */
    private static final String BUNDLED_FONT = "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
    private static final String BUNDLED_BOLD = "net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf";
    private BaseFont boldFont;

    /**
     * Font for PDFs: the first existing file from app.pdf.fonts (e.g. Tahoma on Windows, Cairo you uploaded),
     * otherwise the bundled DejaVu Sans. Never falls back to a font without Arabic letters.
     */
    private synchronized BaseFont baseFont() {
        if (baseFont == null) {
            for (String path : props.pdf().fonts()) {
                try {
                    if (Files.exists(Path.of(path))) {
                        baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                        log.info("PDF font: {}", path);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Cannot load PDF font {}: {}", path, e.getMessage());
                }
            }
            if (baseFont == null) {
                baseFont = bundled(BUNDLED_FONT);
                boldFont = bundled(BUNDLED_BOLD);
                log.info("PDF font: bundled DejaVu Sans");
            }
        }
        return baseFont;
    }

    private BaseFont bundled(String resource) {
        try (var in = PdfService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing bundled PDF font " + resource);
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(resource.substring(resource.lastIndexOf('/') + 1),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load bundled PDF font " + resource, e);
        }
    }
}
