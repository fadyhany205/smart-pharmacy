package com.pharmacy.entity;

import com.pharmacy.util.L10n;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A medicine or other pharmacy item.
 * <ul>
 *   <li>Pack structure: 1 box = stripsPerBox strips, 1 strip = unitsPerStrip units (tablets, ampoules...).</li>
 *   <li>Prices (salePrice = public price, costPrice) are <b>per box</b>.</li>
 *   <li>stockQty is kept in the <b>smallest unit</b> (whole numbers) and is always the sum of the batches.</li>
 *   <li>minStock is in <b>boxes</b>.</li>
 * </ul>
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_name_ar", columnList = "nameAr"),
        @Index(name = "idx_product_name_en", columnList = "nameEn"),
        @Index(name = "idx_product_scientific", columnList = "scientificName")
})
@Getter @Setter
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String barcode;

    @Column(length = 64)
    private String sku;

    /** Trade name. */
    @Column(nullable = false, length = 200)
    private String nameAr;

    @Column(length = 200)
    private String nameEn;

    /** Active ingredient(s), e.g. "Paracetamol". Used to find substitutes. */
    @Column(length = 500)
    private String scientificName;

    /** Id in an imported drug list (e.g. "EGD-123"); used to update prices on re-import. */
    @Column(unique = true, length = 40)
    private String externalRef;

    /** Therapeutic class from the imported list, e.g. "antibiotic.quinolone". */
    @Column(length = 260)
    private String therapeuticClass;

    /** Short usage description (Arabic / English). */
    @Column(columnDefinition = "TEXT")
    private String usesSummary;

    @Column(columnDefinition = "TEXT")
    private String usesSummaryEn;

    /** Safety warnings (Arabic / English). */
    @Column(columnDefinition = "TEXT")
    private String warningsSummary;

    @Column(columnDefinition = "TEXT")
    private String warningsSummaryEn;

    /** Comma separated warning flags: HBP, DIABETES, PREGNANCY, LACTATION, KIDNEY, LIVER, HEART. */
    @Column(length = 100)
    private String warnFlags;

    /** e.g. "500mg", "250mg/5ml". */
    @Column(length = 50)
    private String strength;

    /** e.g. أقراص / شراب / حقن / كريم. */
    @Column(length = 50)
    private String dosageForm;

    @Column(length = 150)
    private String manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    /** Name of the smallest unit (قرص / أمبول / كيس / زجاجة ...). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Unit unit;

    @Column(nullable = false)
    private Integer stripsPerBox = 1;

    @Column(nullable = false)
    private Integer unitsPerStrip = 1;

    /** Cost per box (weighted average or last purchase, see Settings). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    /** Public price per box (سعر الجمهور). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice = BigDecimal.ZERO;

    /** In the smallest unit. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal stockQty = BigDecimal.ZERO;

    /** In boxes. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(length = 50)
    private String shelfLocation;

    private boolean needsFridge;

    private boolean prescriptionRequired;

    /** VAT applies (cosmetics...). Medicines are usually exempt. */
    private boolean taxable;

    @Column(length = 255)
    private String imagePath;

    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public String getName() { return L10n.pick(nameAr, nameEn); }

    @Transient
    public String getUses() { return L10n.pick(usesSummary, usesSummaryEn); }

    @Transient
    public String getWarnings() { return L10n.pick(warningsSummary, warningsSummaryEn); }

    /** Warning flag codes as a list (empty when none). */
    @Transient
    public java.util.List<String> getWarnFlagList() {
        return warnFlags == null || warnFlags.isBlank() ? java.util.List.of() : java.util.List.of(warnFlags.split(","));
    }

    /** Smallest units in one box. */
    @Transient
    public int getFactor() { return Math.max(1, nz(stripsPerBox)) * Math.max(1, nz(unitsPerStrip)); }

    /** Smallest units in one unit of the given sell unit. */
    @Transient
    public int factorOf(SellUnit u) {
        return switch (u == null ? SellUnit.BOX : u) {
            case BOX -> getFactor();
            case STRIP -> Math.max(1, nz(unitsPerStrip));
            case UNIT -> 1;
        };
    }

    /** Which sell units make sense for this pack (a box of 1 tablet can only be sold as a box). */
    @Transient
    public boolean canSell(SellUnit u) {
        return switch (u) {
            case BOX -> true;
            case STRIP -> nz(stripsPerBox) > 1 && nz(unitsPerStrip) > 1;
            case UNIT -> getFactor() > 1;
        };
    }

    /** Price of one sell unit, derived from the box price. */
    @Transient
    public BigDecimal priceOf(SellUnit u) {
        return salePrice.multiply(BigDecimal.valueOf(factorOf(u)))
                .divide(BigDecimal.valueOf(getFactor()), 2, RoundingMode.HALF_UP);
    }

    /** Cost of one sell unit, derived from the box cost (kept with 4 decimals for accurate profit). */
    @Transient
    public BigDecimal costOf(SellUnit u) {
        return costPrice.multiply(BigDecimal.valueOf(factorOf(u)))
                .divide(BigDecimal.valueOf(getFactor()), 4, RoundingMode.HALF_UP);
    }

    /** Stock in boxes (may be fractional). */
    @Transient
    public BigDecimal getStockBoxes() {
        return stockQty.divide(BigDecimal.valueOf(getFactor()), 3, RoundingMode.HALF_UP);
    }

    @Transient
    public boolean isLowStock() { return stockQty.compareTo(minStock.multiply(BigDecimal.valueOf(getFactor()))) <= 0; }

    private static int nz(Integer i) { return i == null ? 1 : i; }
}
