package com.fcms.repository;

import com.fcms.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByGroupKeyOrderByStartDateAsc(String groupKey);
}
