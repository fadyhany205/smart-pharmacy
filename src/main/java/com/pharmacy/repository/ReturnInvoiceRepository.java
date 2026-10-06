package com.pharmacy.repository;

import com.pharmacy.entity.PartyType;
import com.pharmacy.entity.ReturnInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReturnInvoiceRepository extends JpaRepository<ReturnInvoice, Long> {
    @Query("select r from ReturnInvoice r where r.type = :type and r.date between :from and :to")
    Page<ReturnInvoice> search(@Param("type") PartyType type, @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to, Pageable pageable);

    List<ReturnInvoice> findBySaleCustomerIdOrderByDateAsc(Long customerId);

    List<ReturnInvoice> findBySaleId(Long saleId);

    List<ReturnInvoice> findByPurchaseId(Long purchaseId);

    List<ReturnInvoice> findByPurchaseSupplierIdOrderByDateAsc(Long supplierId);
}
