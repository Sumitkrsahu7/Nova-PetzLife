package com.novapetzlife.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    List<InventoryBatch> findByProductIdAndIsActiveTrueOrderByExpiryDateAsc(Long productId);
    List<InventoryBatch> findByExpiryDateBeforeAndIsActiveTrue(LocalDate date);
    List<InventoryBatch> findByExpiryDateBetweenAndIsActiveTrue(LocalDate start, LocalDate end);
}
