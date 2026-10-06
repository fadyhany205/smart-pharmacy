package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases", indexes = @Index(name = "idx_purchase_date", columnList = "date"))
@Getter @Setter
public class Purchase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 30)
    private String invoiceNo;

    /** The invoice number printed on the supplier invoice. */
    @Column(length = 50)
    private String supplierInvoiceNo;

    @Column(nullable = false)
    private LocalDateTime date = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /** Set when the invoice discount was entered as a percentage; discount holds the amount. */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Net amount (after invoice discount) of the lines that are subject to VAT. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal returnedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.COMPLETED;

    @Column(length = 255)
    private String notes;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseItem> items = new ArrayList<>();

    @Transient
    public BigDecimal getRemaining() { return total.subtract(paid).max(BigDecimal.ZERO); }
}
