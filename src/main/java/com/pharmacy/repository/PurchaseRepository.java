package com.pharmacy.repository;

import com.pharmacy.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByInvoiceNo(String invoiceNo);

    @EntityGraph(attributePaths = "supplier")
    @Query(value = """
            select p from Purchase p left join p.supplier s
            where p.date between :from and :to
              and (:q is null or p.invoiceNo like concat('%', :q, '%') or p.supplierInvoiceNo like concat('%', :q, '%')
                   or s.name like concat('%', :q, '%'))
            """,
            countQuery = """
            select count(p) from Purchase p left join p.supplier s
            where p.date between :from and :to
              and (:q is null or p.invoiceNo like concat('%', :q, '%') or p.supplierInvoiceNo like concat('%', :q, '%')
                   or s.name like concat('%', :q, '%'))
            """)
    Page<Purchase> search(@Param("q") String q, @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to, Pageable pageable);

    List<Purchase> findBySupplierIdOrderByDateAsc(Long supplierId);
}
