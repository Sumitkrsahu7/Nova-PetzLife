package com.novapetzlife.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
    List<Refund> findByPaymentId(Long paymentId);
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
    Optional<Refund> findByReceipt(String receipt);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment LEFT JOIN FETCH r.order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.user WHERE r.id = :id")
    Optional<Refund> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment LEFT JOIN FETCH r.order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.user WHERE (r.status = 'INITIATED' OR (r.status = 'PROCESSING' AND r.gatewayRefundId IS NULL)) AND (r.claimExpiresAt IS NULL OR r.claimExpiresAt < :now) AND r.createdAt <= :threshold ORDER BY r.createdAt ASC")
    List<Refund> findPendingRecoveryCandidates(@org.springframework.data.repository.query.Param("now") Instant now, @org.springframework.data.repository.query.Param("threshold") Instant threshold);

    default List<Refund> findPendingRecoveryCandidates(Instant threshold) {
        return findPendingRecoveryCandidates(Instant.now(), threshold);
    }
    List<Refund> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
    List<Refund> findByCompletedAtBetweenAndStatusInOrderByCompletedAtDesc(Instant start, Instant end, java.util.Collection<String> statuses);
}
