package com.pharmacy.repository;

import com.pharmacy.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @Query("select s from Supplier s where :q is null or s.name like concat('%', :q, '%') or s.phone like concat('%', :q, '%')")
    Page<Supplier> search(@Param("q") String q, Pageable pageable);

    List<Supplier> findAllByOrderByNameAsc();
}
