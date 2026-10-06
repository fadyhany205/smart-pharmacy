package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "return_items")
@Getter @Setter
public class ReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReturnInvoice returnInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(length = 200)
    private String productName;

    /** In the sell unit of the original line (sales) or boxes (purchases). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SellUnit sellUnit = SellUnit.BOX;

    /** Quantity in the smallest unit. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal baseQty = BigDecimal.ZERO;

    /** Net unit price after invoice discount, before tax. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    private boolean taxable;
}
