package com.pharmacy.repository;

import com.pharmacy.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    @EntityGraph(attributePaths = {"product", "user", "batch"})
    @Query(value = """
            select m from StockMovement m
            where m.date between :from and :to and (:productId is null or m.product.id = :productId)
            """,
            countQuery = """
            select count(m) from StockMovement m
            where m.date between :from and :to and (:productId is null or m.product.id = :productId)
            """)
    Page<StockMovement> search(@Param("productId") Long productId, @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to, Pageable pageable);
}
