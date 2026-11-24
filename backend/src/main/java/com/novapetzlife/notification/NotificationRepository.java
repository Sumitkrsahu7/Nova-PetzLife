package com.novapetzlife.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    List<Notification> findTop20ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    boolean existsByRecipientAndTemplateAndCreatedAtAfter(String recipient, String template, java.time.Instant after);
}
