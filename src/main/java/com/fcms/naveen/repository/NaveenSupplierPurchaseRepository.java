package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenSupplierPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenSupplierPurchaseRepository extends JpaRepository<NaveenSupplierPurchase, Long> {
    List<NaveenSupplierPurchase> findBySupplierIdOrderByDateDesc(Long supplierId);
}
