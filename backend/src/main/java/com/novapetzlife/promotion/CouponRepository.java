package com.novapetzlife.promotion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);
    List<Coupon> findByIsActiveTrue();
    List<Coupon> findByIsActiveTrueAndStartDateBeforeAndEndDateAfter(Instant now1, Instant now2);
    Page<Coupon> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
