package com.pharmacy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier extends Party {
}
