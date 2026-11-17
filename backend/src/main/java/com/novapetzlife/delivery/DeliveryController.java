package com.novapetzlife.delivery;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Delivery & Geolocation", description = "Kuravankonam 20 km Haversine delivery eligibility, time slots, and store location controls")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/api/v1/delivery/check-eligibility")
    @Operation(summary = "Check delivery eligibility", description = "Computes exact Haversine straight-line distance from Petzlife Kuravankonam store and checks 20 km boundary")
    public ResponseEntity<ApiResponse<DeliveryEligibilityResponse>> checkEligibility(@Valid @RequestBody DeliveryCheckRequest request) {
        DeliveryEligibilityResponse response = deliveryService.checkDeliveryEligibility(
                request.latitude(),
                request.longitude(),
                request.orderSubtotal()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/delivery/slots")
    @Operation(summary = "Get active delivery slots", description = "Retrieves available same-day delivery windows (Morning, Afternoon, Evening)")
    public ResponseEntity<ApiResponse<List<DeliverySlotDto>>> getSlots() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.getActiveDeliverySlots()));
    }

    @GetMapping("/api/v1/delivery/shop-info")
    @Operation(summary = "Get shop location and delivery parameters", description = "Retrieves verified Petzlife Kuravankonam store coordinates, delivery radius, and policy thresholds")
    public ResponseEntity<ApiResponse<ShopLocationDto>> getShopInfo() {
        ShopLocation shop = deliveryService.getShopLocation();
        return ResponseEntity.ok(ApiResponse.ok(ShopLocationDto.fromEntity(shop)));
    }

    // --- Admin Endpoints ---

    @GetMapping("/api/v1/admin/delivery/shop-location")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Get shop location (Admin)", description = "Retrieve configured store coordinates and verification status")
    public ResponseEntity<ApiResponse<ShopLocationDto>> getAdminShopLocation() {
        return ResponseEntity.ok(ApiResponse.ok(ShopLocationDto.fromEntity(deliveryService.getShopLocation())));
    }

    @PatchMapping("/api/v1/admin/delivery/shop-location/verify")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Verify or revoke store coordinates (Super Admin)", description = "Owner approval toggle to activate or deactivate delivery distance calculation")
    public ResponseEntity<ApiResponse<ShopLocationDto>> verifyShopLocation(@RequestBody Map<String, Boolean> body) {
        boolean verified = Boolean.TRUE.equals(body.get("isVerified"));
        ShopLocationDto updated = deliveryService.verifyShopCoordinates(verified);
        String msg = verified ? "Store location verified and approved by owner" : "Store location verification revoked";
        return ResponseEntity.ok(ApiResponse.ok(msg, updated));
    }

    @PutMapping("/api/v1/admin/delivery/shop-location")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update store address and coordinates (Super Admin)", description = "Modify shop address, coordinates, and delivery radius")
    public ResponseEntity<ApiResponse<ShopLocationDto>> updateShopLocation(@Valid @RequestBody ShopLocationUpdateRequest request) {
        ShopLocationDto updated = deliveryService.updateShopLocation(request);
        return ResponseEntity.ok(ApiResponse.ok("Shop location updated successfully. Note: Requires owner re-verification.", updated));
    }

    @GetMapping("/api/v1/admin/delivery/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Get delivery settings (Admin)", description = "Retrieve delivery charge, free delivery threshold, and minimum order value")
    public ResponseEntity<ApiResponse<DeliverySettings>> getDeliverySettings() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.getDeliverySettings()));
    }

    @PutMapping("/api/v1/admin/delivery/settings")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update delivery settings (Super Admin)", description = "Modify delivery fee structure and free delivery threshold")
    public ResponseEntity<ApiResponse<DeliverySettings>> updateDeliverySettings(@RequestBody DeliverySettings settings) {
        DeliverySettings updated = deliveryService.updateDeliverySettings(settings);
        return ResponseEntity.ok(ApiResponse.ok("Delivery settings updated successfully", updated));
    }
}
