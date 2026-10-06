package com.pharmacy.entity;

import com.pharmacy.util.L10n;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter @Setter
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nameAr;

    @Column(length = 100)
    private String nameEn;

    @Transient
    public String getName() { return L10n.pick(nameAr, nameEn); }
}
