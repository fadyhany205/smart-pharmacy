package com.pharmacy.repository;

import com.pharmacy.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCustomerIdOrderByDateAsc(Long customerId);

    List<Payment> findBySupplierIdOrderByDateAsc(Long supplierId);
}
