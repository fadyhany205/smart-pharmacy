package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Which batch(es) a sale line was taken from, so a return goes back to the same batch. Quantities in smallest units. */
@Entity
@Table(name = "batch_allocations")
@Getter @Setter
public class BatchAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch batch;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;
}
