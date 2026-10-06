package com.pharmacy.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Egyptian Drug Database" (eg_drugs.csv / eg_drugs.json):
 * id, name, arabic, active, company, price ("40.00."), barcode, units (strips per box), description (class),
 * uses_summary, uses_summary_en, warning_* flags (0/1), warnings_summary, warnings_summary_en.
 */
public class EgDrugsFormat implements SourceFormat {

    private static final String[][] FLAGS = {
            {"warning_high_blood_pressure", "HBP"}, {"warning_diabetes", "DIABETES"}, {"warning_pregnancy", "PREGNANCY"},
            {"warning_lactation", "LACTATION"}, {"warning_kidney", "KIDNEY"}, {"warning_liver", "LIVER"}, {"warning_heart", "HEART"}};

    @Override public String code() { return "EGD"; }

    @Override public String displayName() { return "eg_drugs (26K, barcodes + warnings; non-commercial license)"; }

    @Override
    public boolean matches(Set<String> columns) {
        return columns.containsAll(Set.of("id", "name", "arabic", "active", "price"));
    }

    @Override
    public DrugRecord map(Map<String, String> r) {
        List<String> flags = new ArrayList<>();
        for (String[] f : FLAGS) if (isOne(r.get(f[0]))) flags.add(f[1]);
        return new DrugRecord(
                blank(r.get("id")),
                blank(r.get("name")),
                blank(r.get("arabic")),
                blank(r.get("active")),
                blank(r.get("company")),
                price(r.get("price")),
                blank(r.get("barcode")),
                intOrNull(r.get("units")),
                blank(r.get("description")),
                blank(r.get("uses_summary")),
                blank(r.get("uses_summary_en")),
                blank(r.get("warnings_summary")),
                blank(r.get("warnings_summary_en")),
                flags.isEmpty() ? null : String.join(",", flags),
                null);
    }

    /** "40.00." / " 75 " / ".76" -> number; empty or invalid -> null. */
    static BigDecimal price(String s) {
        if (s == null) return null;
        String t = s.trim().replace(",", "");
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        if (t.isEmpty()) return null;
        try {
            BigDecimal v = new BigDecimal(t);
            return v.signum() > 0 ? v.setScale(2, java.math.RoundingMode.HALF_UP) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer intOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            int v = (int) Double.parseDouble(s.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isOne(String s) {
        return s != null && (s.trim().equals("1") || s.trim().equals("1.0") || s.trim().equalsIgnoreCase("true"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
