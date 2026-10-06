package com.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Shared fields of customers and suppliers. Balance &gt; 0 means: customer owes us / we owe the supplier. */
@MappedSuperclass
@Getter @Setter
public abstract class Party {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 50)
    private String taxNumber;

    @Column(length = 255)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
}
