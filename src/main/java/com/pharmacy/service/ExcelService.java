package com.pharmacy.service;

import com.pharmacy.config.AppProperties;
import com.pharmacy.dto.ReportTable;
import com.pharmacy.util.Fmt;
import com.pharmacy.util.L10n;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExcelService {
    private final Fmt fmt;
    private final SettingsService settings;
    private final AppProperties props;

    public ExcelService(Fmt fmt, SettingsService settings, AppProperties props) {
        this.fmt = fmt;
        this.settings = settings;
        this.props = props;
    }

    public byte[] report(ReportTable report, LocalDate from, LocalDate to) {
        // Streaming workbook: keeps only 200 rows in memory (large reports on a small server heap).
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(fmt.msg(report.getTitleKey())));
            sheet.setRightToLeft(L10n.isArabic());

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle bold = wb.createCellStyle();
            bold.setFont(boldFont);
            CellStyle head = wb.createCellStyle();
            head.setFont(boldFont);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.cloneStyleFrom(money);
            moneyBold.setFont(boldFont);

            int r = 0;
            cell(sheet.createRow(r++), 0, settings.get().getStoreName(), bold);
            cell(sheet.createRow(r++), 0, fmt.msg(report.getTitleKey()), bold);
            if (from != null) cell(sheet.createRow(r++), 0, fmt.msg("report.period", fmt.date(from), fmt.date(to)), null);
            r++;

            for (ReportTable.Summary s : report.getSummary()) {
                Row row = sheet.createRow(r++);
                cell(row, 0, fmt.msg(s.labelKey()), bold);
                value(row, 1, s.value(), s.highlight() ? moneyBold : money);
            }
            if (!report.getSummary().isEmpty()) r++;

            Row hr = sheet.createRow(r++);
            List<String> heads = report.getHeaderKeys();
            for (int i = 0; i < heads.size(); i++) cell(hr, i, fmt.msg(heads.get(i)), head);
            for (List<Object> cells : report.getRows()) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < cells.size(); i++) value(row, i, cells.get(i), money);
            }
            if (report.getTotals() != null) {
                Row row = sheet.createRow(r++);
                List<Object> tot = report.getTotals();
                cell(row, 0, fmt.msg(String.valueOf(tot.get(0))), bold);
                for (int i = 1; i < tot.size(); i++) value(row, i, tot.get(i), moneyBold);
            }
            r++;
            cell(sheet.createRow(r), 0, fmt.msg("footer.poweredByLine", props.vendor().name(), props.vendor().phone()), null);
            setColumnWidths(sheet, report);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            wb.dispose();   // delete the temporary files
            try { wb.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Column widths from the header and the first rows. (sheet.autoSizeColumn measures every cell with
     * font metrics and took ~9 s for 10,000 rows.)
     */
    private void setColumnWidths(Sheet sheet, ReportTable report) {
        List<String> heads = report.getHeaderKeys();
        int sample = Math.min(report.getRows().size(), 300);
        for (int c = 0; c < heads.size(); c++) {
            int chars = fmt.msg(heads.get(c)).length();
            for (int r = 0; r < sample; r++) {
                List<Object> row = report.getRows().get(r);
                if (c < row.size()) chars = Math.max(chars, fmt.cell(row.get(c)).length());
            }
            if (report.getTotals() != null && c < report.getTotals().size()) {
                chars = Math.max(chars, fmt.cell(report.getTotals().get(c)).length());
            }
            sheet.setColumnWidth(c, Math.min(chars + 3, 60) * 256);
        }
    }

    private void value(Row row, int col, Object v, CellStyle numStyle) {
        if (v instanceof BigDecimal b) {
            Cell c = row.createCell(col);
            c.setCellValue(b.doubleValue());
            if (b.scale() != 3) c.setCellStyle(numStyle);
        } else if (v instanceof Number n) {
            row.createCell(col).setCellValue(n.doubleValue());
        } else {
            cell(row, col, fmt.cell(v), null);
        }
    }

    private void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        if (style != null) c.setCellStyle(style);
    }

    private static String safeSheetName(String s) {
        String cleaned = s.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
