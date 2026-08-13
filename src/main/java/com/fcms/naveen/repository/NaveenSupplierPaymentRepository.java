package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenSupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenSupplierPaymentRepository extends JpaRepository<NaveenSupplierPayment, Long> {
    List<NaveenSupplierPayment> findBySupplierIdOrderByDateDesc(Long supplierId);
}
