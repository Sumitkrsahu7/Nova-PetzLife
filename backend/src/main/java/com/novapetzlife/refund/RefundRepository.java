package com.novapetzlife.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    List<Refund> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
    List<Refund> findByCompletedAtBetweenAndStatusInOrderByCompletedAtDesc(Instant start, Instant end, java.util.Collection<String> statuses);
}
