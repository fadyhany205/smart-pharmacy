package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Money received from a customer or paid to a supplier outside of an invoice. */
@Entity
@Table(name = "payments")
@Getter @Setter
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyType partyType;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method = PaymentMethod.CASH;

    /**
     * false (normal): money received from a customer / paid to a supplier - the balance decreases.
     * true (refund):  cash paid out to a customer / received back from a supplier - the balance increases.
     * Nullable column so existing databases upgrade cleanly.
     */
    @Column(name = "refund")
    private Boolean refund = false;

    @Column(length = 255)
    private String note;

    public boolean isRefund() { return Boolean.TRUE.equals(refund); }

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
