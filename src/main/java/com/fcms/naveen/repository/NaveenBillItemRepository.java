package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenBillItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenBillItemRepository extends JpaRepository<NaveenBillItem, Long> {
    List<NaveenBillItem> findByBillIdOrderByIdAsc(Long billId);
}
