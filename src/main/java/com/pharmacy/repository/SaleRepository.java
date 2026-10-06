package com.pharmacy.repository;

import com.pharmacy.entity.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findByInvoiceNo(String invoiceNo);

    @EntityGraph(attributePaths = {"customer", "user"})
    @Query(value = """
            select s from Sale s left join s.customer c
            where s.date between :from and :to
              and (:q is null or s.invoiceNo like concat('%', :q, '%') or c.name like concat('%', :q, '%'))
            """,
            countQuery = """
            select count(s) from Sale s left join s.customer c
            where s.date between :from and :to
              and (:q is null or s.invoiceNo like concat('%', :q, '%') or c.name like concat('%', :q, '%'))
            """)
    Page<Sale> search(@Param("q") String q, @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to, Pageable pageable);

    List<Sale> findByCustomerIdOrderByDateAsc(Long customerId);

    List<Sale> findTop8ByOrderByDateDesc();
}
