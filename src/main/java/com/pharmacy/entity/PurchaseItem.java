package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Purchase line. Quantities are in boxes. */
@Entity
@Table(name = "purchase_items")
@Getter @Setter
public class PurchaseItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(length = 200)
    private String productName;

    /** Paid boxes. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    /** Free boxes from the supplier (بونص). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal bonusQty = BigDecimal.ZERO;

    /** Cost per box before the line discount. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    /** Supplier discount on this line, %. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** qty × unitCost − discount. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    /** The batch created by this line. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;

    private boolean taxable;

    /** Boxes returned to the supplier (paid or bonus). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Transient
    public BigDecimal getTotalQty() { return qty.add(bonusQty); }

    @Transient
    public BigDecimal getReturnableQty() { return getTotalQty().subtract(returnedQty); }
}
