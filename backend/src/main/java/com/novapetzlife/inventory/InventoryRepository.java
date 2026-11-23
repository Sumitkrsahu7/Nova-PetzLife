package com.novapetzlife.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndVariantId(Long productId, Long variantId);

    Optional<Inventory> findByProductIdAndVariantIsNull(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.variant.id = :variantId")
    Optional<Inventory> findByProductIdAndVariantIdForUpdate(@Param("productId") Long productId, @Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.variant IS NULL")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    @Query("SELECT i FROM Inventory i WHERE i.availableStock <= i.minStockThreshold")
    List<Inventory> findLowStockItems();

    @Query("SELECT i FROM Inventory i WHERE i.availableStock = 0")
    List<Inventory> findOutOfStockItems();

    List<Inventory> findByProductId(Long productId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Inventory i WHERE i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(i.availableStock), 0) FROM Inventory i WHERE i.product.id = :productId")
    Integer findTotalAvailableStockByProductId(@Param("productId") Long productId);
}
