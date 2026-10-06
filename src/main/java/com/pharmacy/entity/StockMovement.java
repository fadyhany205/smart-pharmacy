package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = @Index(name = "idx_movement_date", columnList = "date"))
@Getter @Setter
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(nullable = false)
    private LocalDateTime date = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Signed quantity: positive = in, negative = out. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal balanceAfter;

    @Column(length = 30)
    private String refNo;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /** The batch affected (all stock changes are per batch). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;
}
