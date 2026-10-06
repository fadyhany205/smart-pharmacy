package com.pharmacy.importer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * "Egyptian Drug Database" (egyptian-drugs.csv / .json, CC0 public domain):
 * commercial_name_en, commercial_name_ar (transliteration, no size), scientific_name, manufacturer,
 * drug_class, route, price_egp. The file has no id, so a stable id is derived from the English name.
 */
public class EgyptianDrugDbFormat implements SourceFormat {

    @Override public String code() { return "EDD"; }

    @Override public String displayName() { return "egyptian-drug-database (25K, CC0 free license)"; }

    @Override
    public boolean matches(Set<String> columns) {
        return columns.containsAll(Set.of("commercial_name_en", "commercial_name_ar", "price_egp"));
    }

    @Override
    public DrugRecord map(Map<String, String> r) {
        String en = blank(r.get("commercial_name_en"));
        String cls = blank(r.get("drug_class"));
        if (".".equals(cls)) cls = null;
        return new DrugRecord(
                en == null ? null : stableId(en),
                en,
                blank(r.get("commercial_name_ar")),
                blank(r.get("scientific_name")),
                blank(r.get("manufacturer")),
                EgDrugsFormat.price(r.get("price_egp")),
                null,          // no barcodes in this source
                null,          // no strips column: taken from the name
                cls,
                null, null, null, null, null,
                blank(r.get("route")));
    }

    /** Same name -> same id on every import (used to update prices later). */
    static String stableId(String nameEn) {
        String norm = nameEn.toLowerCase().replaceAll("[^a-z0-9]", "");
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(norm.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (Exception e) {
            return Integer.toHexString(norm.hashCode());
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
