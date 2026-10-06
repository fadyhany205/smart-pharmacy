package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A received lot of a product (رقم التشغيلة) with its own expiry date.
 * qty is in the product's smallest unit. The product stock is the sum of its batches.
 */
@Entity
@Table(name = "batches", indexes = {
        @Index(name = "idx_batch_product", columnList = "product_id"),
        @Index(name = "idx_batch_expiry", columnList = "expiryDate")
})
@Getter @Setter
public class Batch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(length = 50)
    private String batchNo;

    /** Null = does not expire (e.g. some accessories). */
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty = BigDecimal.ZERO;

    /** Effective cost per box of this lot (after supplier discount and bonus). */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal costPerBox = BigDecimal.ZERO;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /** Expires within the given number of days (and not already expired). */
    @Transient
    public boolean expiresWithin(int days) {
        return expiryDate != null && !isExpired() && !expiryDate.isAfter(LocalDate.now().plusDays(days));
    }
}
