package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenBillRepository extends JpaRepository<NaveenBill, Long> {
    List<NaveenBill> findAllByOrderByDateDescIdDesc();
}
