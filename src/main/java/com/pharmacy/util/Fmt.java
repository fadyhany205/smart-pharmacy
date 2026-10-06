package com.pharmacy.util;

import com.pharmacy.dto.ReportTable;
import com.pharmacy.entity.Product;
import com.pharmacy.entity.SellUnit;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatting helpers, available in templates as ${@fmt.money(x)} etc.
 * Western digits are used in both languages (common for invoices in Egypt).
 */
@Component("fmt")
public class Fmt implements com.pharmacy.dto.ProductLookup.Names {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final MessageSource messages;

    public Fmt(MessageSource messages) {
        this.messages = messages;
    }

    // DecimalFormat is not thread-safe: one cached instance per thread.
    private static final ThreadLocal<DecimalFormat> MONEY =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> QTY =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    public String money(BigDecimal v) {
        return v == null ? "" : MONEY.get().format(v);
    }

    public String qty(BigDecimal v) {
        return v == null ? "" : QTY.get().format(v);
    }

    /**
     * A quantity in the smallest unit shown as boxes / strips / units, e.g. "3 علبة + 1 شريط + 4 قرص".
     */
    public String stock(Product p, BigDecimal baseQty) {
        if (p == null || baseQty == null) return "";
        long total = baseQty.setScale(0, RoundingMode.HALF_UP).longValue();
        String sign = total < 0 ? "-" : "";
        total = Math.abs(total);
        int factor = p.getFactor();
        boolean strips = p.canSell(SellUnit.STRIP);
        int perStrip = Math.max(1, p.getUnitsPerStrip() == null ? 1 : p.getUnitsPerStrip());
        long boxes = total / factor, rest = total % factor;
        long s = strips ? rest / perStrip : 0;
        long u = strips ? rest % perStrip : rest;
        StringBuilder sb = new StringBuilder();
        if (boxes > 0 || (s == 0 && u == 0)) sb.append(boxes).append(' ').append(unitName(p, SellUnit.BOX));
        if (s > 0) sb.append(sb.isEmpty() ? "" : " + ").append(s).append(' ').append(unitName(p, SellUnit.STRIP));
        if (u > 0) sb.append(sb.isEmpty() ? "" : " + ").append(u).append(' ').append(unitName(p, SellUnit.UNIT));
        return sign + sb;
    }

    public String stock(Product p) {
        return p == null ? "" : stock(p, p.getStockQty());
    }

    /** Box / strip / the product's smallest unit name (قرص, أمبول...). */
    public String unitName(Product p, SellUnit u) {
        return switch (u) {
            case BOX -> msg("sellUnit.BOX");
            case STRIP -> msg("sellUnit.STRIP");
            case UNIT -> p != null && p.getUnit() != null ? p.getUnit().getName() : msg("sellUnit.UNIT");
        };
    }

    /** " (10%)" when a discount was entered as a percentage, otherwise "". */
    public String pct(BigDecimal p) {
        return p == null ? "" : " (" + p.stripTrailingZeros().toPlainString() + "%)";
    }

    public String date(Object d) {
        if (d instanceof LocalDateTime t) return t.format(DATE);
        if (d instanceof LocalDate t) return t.format(DATE);
        return d == null ? "" : d.toString();
    }

    public String dateTime(LocalDateTime t) {
        return t == null ? "" : t.format(DATE_TIME);
    }

    public String msg(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /** Text of a report cell. BigDecimal with 3 decimals = quantity, otherwise money. */
    public String cell(Object v) {
        if (v == null) return "";
        if (v instanceof ReportTable.Msg m) return msg(m.key());
        if (v instanceof BigDecimal b) return b.scale() == 3 ? qty(b) : money(b);
        if (v instanceof LocalDate || v instanceof LocalDateTime) return date(v);
        return v.toString();
    }

    public boolean isNum(Object v) {
        return v instanceof Number;
    }
}
