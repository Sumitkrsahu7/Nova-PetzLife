package com.novapetzlife.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.customer.id = :customerId AND oi.order.orderStatus = 'DELIVERED' AND oi.product.id = :productId")
    boolean isVerifiedPurchase(@Param("customerId") Long customerId, @Param("productId") Long productId);
}
