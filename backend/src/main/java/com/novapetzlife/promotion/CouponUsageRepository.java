package com.novapetzlife.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    List<CouponUsage> findByCouponId(Long couponId);
    List<CouponUsage> findByCustomerId(Long customerId);
    long countByCouponIdAndCustomerId(Long couponId, Long customerId);
}
