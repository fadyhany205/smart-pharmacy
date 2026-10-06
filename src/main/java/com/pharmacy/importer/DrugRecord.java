package com.pharmacy.importer;

import java.math.BigDecimal;

/**
 * One drug from an import file, already normalized (independent of the file format).
 * Fields that a source does not provide are null.
 */
public record DrugRecord(
        String sourceId,        // unique id inside the source (used as Product.externalRef)
        String nameEn,
        String nameAr,
        String activeIngredient,
        String manufacturer,
        BigDecimal price,       // public price per box
        String barcode,
        Integer subUnits,       // strips / sachets / ampoules per box (when the source knows it)
        String therapeuticClass,
        String usesAr,
        String usesEn,
        String warningsAr,
        String warningsEn,
        String warnFlags,       // e.g. "PREGNANCY,KIDNEY"
        String route) {         // route of administration when known, e.g. "ORAL.SOLID", "INJECTION"
}
