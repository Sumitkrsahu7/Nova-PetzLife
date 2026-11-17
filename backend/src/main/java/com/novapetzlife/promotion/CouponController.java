package com.novapetzlife.promotion;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.promotion.dto.ApplyCouponRequest;
import com.novapetzlife.promotion.dto.CouponDto;
import com.novapetzlife.promotion.dto.CouponValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions/coupons")
@Tag(name = "Coupons & Promotions", description = "Public coupon discovery and cart coupon validation")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/active")
    @Operation(summary = "Get active coupons", description = "List of currently active public promotional discount coupons")
    public ResponseEntity<ApiResponse<List<CouponDto>>> getActiveCoupons() {
        List<CouponDto> coupons = couponService.getActivePublicCoupons();
        return ResponseEntity.ok(ApiResponse.ok(coupons));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate and preview coupon discount", description = "Checks coupon eligibility against order subtotal and computes discount amount")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @Valid @RequestBody ApplyCouponRequest request) {
        CouponValidationResponse response = couponService.validateAndCalculateDiscount(
                request.getCode(),
                request.getCartSubtotal(),
                request.getCustomerId()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
