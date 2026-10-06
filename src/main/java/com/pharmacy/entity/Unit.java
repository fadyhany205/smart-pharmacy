package com.pharmacy.entity;

import com.pharmacy.util.L10n;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "units")
@Getter @Setter
public class Unit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nameAr;

    @Column(length = 50)
    private String nameEn;

    @Transient
    public String getName() { return L10n.pick(nameAr, nameEn); }
}
