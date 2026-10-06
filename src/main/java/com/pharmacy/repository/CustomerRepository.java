package com.pharmacy.repository;

import com.pharmacy.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("select c from Customer c where :q is null or c.name like concat('%', :q, '%') or c.phone like concat('%', :q, '%')")
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    List<Customer> findAllByOrderByNameAsc();
}
