package com.novapetzlife.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    Optional<Order> findByOrderNumberAndCustomerId(String orderNumber, Long customerId);
    java.util.List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.Instant start, java.time.Instant end);
    java.util.List<Order> findByPaidAtBetweenOrderByPaidAtDesc(java.time.Instant start, java.time.Instant end);
    java.util.List<Order> findByOrderStatusAndCreatedAtBefore(String orderStatus, java.time.Instant cutoffTime);
    Page<Order> findByOrderStatusAndCreatedAtBefore(String orderStatus, java.time.Instant cutoffTime, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
}
