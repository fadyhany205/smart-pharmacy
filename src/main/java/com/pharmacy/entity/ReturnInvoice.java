package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sales return (from a customer) or a purchase return (back to a supplier). */
@Entity
@Table(name = "returns", indexes = @Index(name = "idx_return_date", columnList = "date"))
@Getter @Setter
public class ReturnInvoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 30)
    private String returnNo;

    /** CUSTOMER = sales return, SUPPLIER = purchase return. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyType type;

    @ManyToOne(fetch = FetchType.LAZY)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    private Purchase purchase;

    @Column(nullable = false)
    private LocalDateTime date = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /** Amount incl. tax refunded (or credited to the party balance). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Net amount (after invoice discount) of the lines that are subject to VAT. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    /** Cost value of the returned goods (for profit reports). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costTotal = BigDecimal.ZERO;

    /** Legacy flag (returns created before split settlement): true = all to balance, false = all cash. */
    private boolean toBalance;

    /** Part of the total deducted from the customer/supplier account balance. Null on legacy rows. */
    @Column(precision = 14, scale = 2)
    private BigDecimal balanceAmount;

    /** Part of the total refunded in cash. Null on legacy rows. */
    @Column(precision = 14, scale = 2)
    private BigDecimal cashAmount;

    @Column(length = 255)
    private String reason;

    @OneToMany(mappedBy = "returnInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReturnItem> items = new ArrayList<>();

    @Transient
    public String getOriginalNo() {
        return sale != null ? sale.getInvoiceNo() : purchase != null ? purchase.getInvoiceNo() : "";
    }

    @Transient
    public boolean isSalesReturn() { return type == PartyType.CUSTOMER; }

    public BigDecimal getBalanceAmount() {
        return balanceAmount != null ? balanceAmount : (toBalance ? total : BigDecimal.ZERO);
    }

    public BigDecimal getCashAmount() {
        return cashAmount != null ? cashAmount : (toBalance ? BigDecimal.ZERO : total);
    }
}
