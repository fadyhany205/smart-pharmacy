package com.pharmacy.repository;

import com.pharmacy.entity.BatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchAllocationRepository extends JpaRepository<BatchAllocation, Long> {
    List<BatchAllocation> findBySaleItemIdOrderByIdAsc(Long saleItemId);
}
