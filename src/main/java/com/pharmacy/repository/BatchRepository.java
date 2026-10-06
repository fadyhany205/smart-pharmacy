package com.pharmacy.repository;

import com.pharmacy.entity.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    /**
     * FEFO: batches that can be sold, earliest expiry first (no-expiry batches last).
     * Locked for the transaction so two cashiers cannot take the same stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Batch b
            where b.product.id = :productId and b.qty > 0 and (b.expiryDate is null or b.expiryDate >= :today)
            order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate, b.id
            """)
    List<Batch> findSellableForUpdate(@Param("productId") Long productId, @Param("today") LocalDate today);

    /** Quantity (smallest unit) that can be sold: not expired. */
    @Query("""
            select coalesce(sum(b.qty), 0) from Batch b
            where b.product.id = :productId and b.qty > 0 and (b.expiryDate is null or b.expiryDate >= :today)
            """)
    BigDecimal sellableQty(@Param("productId") Long productId, @Param("today") LocalDate today);

    /** Earliest expiry among batches in stock and not yet expired. */
    @Query("""
            select min(b.expiryDate) from Batch b
            where b.product.id = :productId and b.qty > 0 and b.expiryDate >= :today
            """)
    LocalDate nearestExpiry(@Param("productId") Long productId, @Param("today") LocalDate today);

    List<Batch> findByProductIdOrderByExpiryDateAscIdAsc(Long productId);

    Optional<Batch> findFirstByProductIdAndBatchNoAndExpiryDate(Long productId, String batchNo, LocalDate expiryDate);

    /** Batches in stock expiring on or before a date (includes already expired), earliest first. */
    @Query("""
            select b from Batch b join fetch b.product p
            where b.qty > 0 and b.expiryDate is not null and b.expiryDate <= :until
            order by b.expiryDate, p.nameAr
            """)
    List<Batch> findExpiringUntil(@Param("until") LocalDate until);

    @Query("select count(b) from Batch b where b.qty > 0 and b.expiryDate < :today")
    long countExpired(@Param("today") LocalDate today);

    @Query("select count(b) from Batch b where b.qty > 0 and b.expiryDate >= :today and b.expiryDate <= :until")
    long countExpiring(@Param("today") LocalDate today, @Param("until") LocalDate until);

    @Query("select b from Batch b join fetch b.product p where b.qty > 0 order by p.nameAr, b.expiryDate")
    List<Batch> findAllInStock();
}
