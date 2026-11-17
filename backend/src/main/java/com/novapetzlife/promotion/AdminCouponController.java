package com.novapetzlife.promotion;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.promotion.dto.CouponDto;
import com.novapetzlife.promotion.dto.CreateCouponRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/promotions/coupons")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Promotions & Coupons", description = "Shop administration of promotional codes, discount parameters, and usage limits")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    @Operation(summary = "List all coupons", description = "Paginated list of discount coupons with usage analytics")
    public ResponseEntity<ApiResponse<Page<CouponDto>>> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CouponDto> coupons = couponService.getCoupons(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.ok(coupons));
    }

    @PostMapping
    @Operation(summary = "Create promotional coupon", description = "Creates a new percentage or fixed discount coupon")
    public ResponseEntity<ApiResponse<CouponDto>> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        CouponDto created = couponService.createCoupon(request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "Toggle coupon active status", description = "Activates or deactivates a coupon code")
    public ResponseEntity<ApiResponse<CouponDto>> toggleActive(@PathVariable Long id) {
        CouponDto updated = couponService.toggleCouponActive(id);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon", description = "Removes a coupon from catalog")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
