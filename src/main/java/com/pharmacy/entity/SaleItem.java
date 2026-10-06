package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Getter @Setter
public class SaleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    /** Snapshot of the product name at sale time. */
    @Column(length = 200)
    private String productName;

    /** Quantity in sellUnit (e.g. 2 strips). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SellUnit sellUnit = SellUnit.BOX;

    /** Smallest units in one sellUnit at sale time. */
    @Column(nullable = false)
    private Integer unitFactor = 1;

    /** qty × unitFactor: quantity in the smallest unit (used for stock and reports). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal baseQty = BigDecimal.ZERO;

    /** Price of one sellUnit. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /** Cost of one sellUnit at sale time, used for profit reports. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** VAT applied to this line (snapshot of product.taxable). */
    private boolean taxable;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /** Set when the line discount was entered as a percentage; discount holds the amount. */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Transient
    public BigDecimal getReturnableQty() { return qty.subtract(returnedQty); }
}
