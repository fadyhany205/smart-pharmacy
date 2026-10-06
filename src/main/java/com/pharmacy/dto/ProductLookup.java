package com.pharmacy.dto;

import com.pharmacy.entity.Product;
import com.pharmacy.entity.SellUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Product info for the POS / purchase screens.
 * salePrice / costPrice are per box; stockQty / sellableQty are in the smallest unit.
 * units lists what can be sold (BOX / STRIP / UNIT) with each unit's factor, name and price.
 */
public record ProductLookup(Long id, String barcode, String name, String scientificName, String strength, String dosageForm,
                            BigDecimal salePrice, BigDecimal costPrice, BigDecimal stockQty, BigDecimal sellableQty,
                            String stockText, String unit, int factor, List<UnitOption> units,
                            boolean taxable, boolean prescriptionRequired, boolean needsFridge, String shelfLocation,
                            LocalDate nearestExpiry, List<String> warnFlags, String uses, String warnings) {

    public record UnitOption(SellUnit code, String name, int factor, BigDecimal price) {}

    /** Localized unit names / stock text (implemented by Fmt). */
    public interface Names {
        String unitName(Product p, SellUnit u);
        String stock(Product p, BigDecimal baseQty);
    }

    public static ProductLookup of(Product p, BigDecimal sellable, LocalDate nearestExpiry, Names names) {
        List<UnitOption> units = new ArrayList<>();
        for (SellUnit u : SellUnit.values()) {
            if (p.canSell(u)) units.add(new UnitOption(u, names.unitName(p, u), p.factorOf(u), p.priceOf(u)));
        }
        return new ProductLookup(p.getId(), p.getBarcode(), p.getName(), p.getScientificName(), p.getStrength(), p.getDosageForm(),
                p.getSalePrice(), p.getCostPrice(), p.getStockQty(), sellable, names.stock(p, sellable),
                p.getUnit() == null ? "" : p.getUnit().getName(), p.getFactor(), units,
                p.isTaxable(), p.isPrescriptionRequired(), p.isNeedsFridge(), p.getShelfLocation(), nearestExpiry,
                p.getWarnFlagList(), p.getUses(), p.getWarnings());
    }
}
