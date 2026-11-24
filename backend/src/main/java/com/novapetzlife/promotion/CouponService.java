package com.novapetzlife.promotion;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.order.Order;
import com.novapetzlife.promotion.dto.CouponDto;
import com.novapetzlife.promotion.dto.CouponValidationResponse;
import com.novapetzlife.promotion.dto.CreateCouponRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    public CouponService(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
    }

    @Transactional(readOnly = true)
    public List<CouponDto> getActivePublicCoupons() {
        Instant now = Instant.now();
        return couponRepository.findByIsActiveTrueAndStartDateBeforeAndEndDateAfter(now, now).stream()
                .filter(Coupon::isCurrentlyValid)
                .map(CouponDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponValidationResponse validateAndCalculateDiscount(String code, BigDecimal subtotal, Long customerId) {
        if (code == null || code.isBlank()) {
            return CouponValidationResponse.invalid(code, "Please enter a valid coupon code", subtotal);
        }

        Optional<Coupon> opt = couponRepository.findByCodeIgnoreCase(code.trim());
        if (opt.isEmpty()) {
            return CouponValidationResponse.invalid(code, "Coupon code '" + code + "' does not exist", subtotal);
        }

        Coupon coupon = opt.get();
        if (!coupon.isCurrentlyValid()) {
            return CouponValidationResponse.invalid(code, "Coupon '" + code + "' is inactive or has expired", subtotal);
        }

        if (customerId != null) {
            long usageCount = couponUsageRepository.countByCouponIdAndCustomerId(coupon.getId(), customerId);
            if (usageCount > 0) {
                return CouponValidationResponse.invalid(code, "You have already used this coupon code", subtotal);
            }
        }

        if (subtotal != null && coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            return CouponValidationResponse.invalid(code, "Minimum order amount of ₹" + coupon.getMinOrderValue() + " required to use this coupon", subtotal);
        }

        BigDecimal discount = BigDecimal.ZERO;
        if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())) {
            BigDecimal rate = coupon.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discount = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
        } else {
            // FIXED
            discount = coupon.getDiscountValue();
            if (discount.compareTo(subtotal) > 0) {
                discount = subtotal;
            }
        }

        BigDecimal newSubtotal = subtotal.subtract(discount);
        String msg = "Coupon applied successfully! Saved ₹" + discount;

        return CouponValidationResponse.success(
                coupon.getCode(),
                coupon.getDiscountType(),
                discount,
                subtotal,
                newSubtotal,
                msg
        );
    }

    @Transactional
    public void recordCouponUsage(String code, Customer customer, Order order, BigDecimal discountApplied) {
        if (code == null || code.isBlank()) return;

        couponRepository.findByCodeIgnoreCaseForUpdate(code.trim()).ifPresent(coupon -> {
            if (customer != null && customer.getId() != null) {
                long customerUsage = couponUsageRepository.countByCouponIdAndCustomerId(coupon.getId(), customer.getId());
                if (customerUsage > 0) {
                    throw new IllegalStateException("Customer has already used coupon " + coupon.getCode());
                }
            }
            if (coupon.getUsageLimit() != null && coupon.getUsageCount() >= coupon.getUsageLimit()) {
                throw new IllegalStateException("Coupon usage limit has been reached for " + coupon.getCode());
            }
            coupon.incrementUsage();
            couponRepository.save(coupon);

            CouponUsage usage = new CouponUsage(coupon, customer, order, discountApplied);
            couponUsageRepository.save(usage);

            log.info("Recorded usage of coupon {} for order {} with discount ₹{}",
                    coupon.getCode(), order.getOrderNumber(), discountApplied);
        });
    }

    @Transactional
    public CouponDto createCoupon(CreateCouponRequest req) {
        if (couponRepository.findByCodeIgnoreCase(req.getCode().trim()).isPresent()) {
            throw new IllegalArgumentException("Coupon code '" + req.getCode() + "' already exists");
        }

        Coupon coupon = new Coupon(
                req.getCode(),
                req.getDescription(),
                req.getDiscountType(),
                req.getDiscountValue(),
                req.getMinOrderValue(),
                req.getMaxDiscountAmount(),
                req.getUsageLimit(),
                req.getStartDate(),
                req.getEndDate(),
                req.getIsActive()
        );

        return CouponDto.fromEntity(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public Page<CouponDto> getCoupons(Pageable pageable) {
        return couponRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(CouponDto::fromEntity);
    }

    @Transactional
    public CouponDto toggleCouponActive(Long id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found with id: " + id));
        c.setIsActive(!Boolean.TRUE.equals(c.getIsActive()));
        return CouponDto.fromEntity(couponRepository.save(c));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponRepository.deleteById(id);
    }
}
