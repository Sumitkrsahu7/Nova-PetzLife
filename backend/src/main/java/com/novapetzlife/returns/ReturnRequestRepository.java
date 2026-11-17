package com.novapetzlife.returns;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Optional<ReturnRequest> findByRequestNumber(String requestNumber);
    List<ReturnRequest> findByOrderId(Long orderId);
    Page<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
