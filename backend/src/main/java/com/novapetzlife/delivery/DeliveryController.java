package com.novapetzlife.delivery;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Delivery & Geolocation", description = "Dynamic store delivery eligibility, address geocoding, time slots, and store location controls")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/api/v1/delivery/check-eligibility")
    @Operation(summary = "Check delivery eligibility by coordinates", description = "Computes exact geodesic distance from official store coordinates and checks delivery radius boundary")
    public ResponseEntity<ApiResponse<DeliveryEligibilityResponse>> checkEligibility(@Valid @RequestBody DeliveryCheckRequest request) {
        DeliveryEligibilityResponse response = deliveryService.checkDeliveryEligibility(
                request.latitude(),
                request.longitude(),
                request.orderSubtotal()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/api/v1/delivery/check-address")
    @Operation(summary = "Check delivery availability by customer address", description = "Resolves customer address into coordinates and evaluates 20 km radius eligibility")
    public ResponseEntity<ApiResponse<DeliveryEligibilityResponse>> checkAddressDelivery(@RequestBody AddressDeliveryCheckRequest request) {
        DeliveryEligibilityResponse response = deliveryService.checkAddressDeliveryEligibility(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/delivery/slots")
    @Operation(summary = "Get active delivery slots", description = "Retrieves active customer delivery windows for checkout")
    public ResponseEntity<ApiResponse<List<DeliverySlotDto>>> getSlots() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.getActiveDeliverySlots()));
    }

    @GetMapping("/api/v1/delivery/shop-info")
    @Operation(summary = "Get shop location and delivery parameters", description = "Retrieves verified store coordinates, delivery radius, and policy thresholds")
    public ResponseEntity<ApiResponse<ShopLocationDto>> getShopInfo() {
        ShopLocation shop = deliveryService.getShopLocation();
        return ResponseEntity.ok(ApiResponse.ok(ShopLocationDto.fromEntity(shop)));
    }

    // --- Admin Endpoints ---

    @GetMapping("/api/v1/admin/delivery/slots")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Get all delivery slots (Admin)", description = "Retrieves all active and inactive delivery windows for Store Settings management")
    public ResponseEntity<ApiResponse<List<DeliverySlotDto>>> getAllDeliverySlots() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.getAllDeliverySlots()));
    }

    @PostMapping("/api/v1/admin/delivery/slots")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Create delivery window slot (Admin)", description = "Add a new delivery window with timing and day restrictions")
    public ResponseEntity<ApiResponse<DeliverySlotDto>> createDeliverySlot(@Valid @RequestBody DeliverySlotCreateRequest request) {
        DeliverySlotDto created = deliveryService.createDeliverySlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Delivery slot created successfully", created));
    }

    @PutMapping("/api/v1/admin/delivery/slots/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Update delivery window slot (Admin)", description = "Update timings, capacity, or available days for a delivery slot")
    public ResponseEntity<ApiResponse<DeliverySlotDto>> updateDeliverySlot(
            @PathVariable Long id,
            @Valid @RequestBody DeliverySlotCreateRequest request) {
        DeliverySlotDto updated = deliveryService.updateDeliverySlot(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Delivery slot updated successfully", updated));
    }

    @PatchMapping("/api/v1/admin/delivery/slots/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Toggle delivery window active status (Admin)")
    public ResponseEntity<ApiResponse<DeliverySlotDto>> toggleDeliverySlotStatus(@PathVariable Long id) {
        DeliverySlotDto updated = deliveryService.toggleDeliverySlotStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Delivery slot status updated", updated));
    }

    @DeleteMapping("/api/v1/admin/delivery/slots/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Delete delivery window slot (Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteDeliverySlot(@PathVariable Long id) {
        deliveryService.deleteDeliverySlot(id);
        return ResponseEntity.ok(ApiResponse.ok("Delivery slot deleted successfully", null));
    }

    @GetMapping("/api/v1/admin/delivery/shop-location")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Get shop location (Admin)", description = "Retrieve configured store coordinates and verification status")
    public ResponseEntity<ApiResponse<ShopLocationDto>> getAdminShopLocation() {
        return ResponseEntity.ok(ApiResponse.ok(ShopLocationDto.fromEntity(deliveryService.getShopLocation())));
    }

    @PatchMapping("/api/v1/admin/delivery/shop-location/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Verify or revoke store coordinates (Super Admin)", description = "Owner approval toggle to activate or deactivate delivery distance calculation")
    public ResponseEntity<ApiResponse<ShopLocationDto>> verifyShopLocation(@RequestBody Map<String, Boolean> body) {
        boolean verified = Boolean.TRUE.equals(body.get("isVerified"));
        ShopLocationDto updated = deliveryService.verifyShopCoordinates(verified);
        String msg = verified ? "Store location verified and approved by owner" : "Store location verification revoked";
        return ResponseEntity.ok(ApiResponse.ok(msg, updated));
    }

    @PutMapping("/api/v1/admin/delivery/shop-location")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Update store address and coordinates (Super Admin)", description = "Modify shop address, coordinates, and delivery radius")
    public ResponseEntity<ApiResponse<ShopLocationDto>> updateShopLocation(@Valid @RequestBody ShopLocationUpdateRequest request) {
        ShopLocationDto updated = deliveryService.updateShopLocation(request);
        return ResponseEntity.ok(ApiResponse.ok("Shop location updated successfully", updated));
    }

    @GetMapping("/api/v1/admin/delivery/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Get delivery settings (Admin)", description = "Retrieve delivery charge, free delivery threshold, and minimum order value")
    public ResponseEntity<ApiResponse<DeliverySettings>> getDeliverySettings() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.getDeliverySettings()));
    }

    @PutMapping("/api/v1/admin/delivery/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Update delivery settings (Super Admin)", description = "Modify delivery fee structure and free delivery threshold")
    public ResponseEntity<ApiResponse<DeliverySettings>> updateDeliverySettings(@RequestBody DeliverySettings settings) {
        DeliverySettings updated = deliveryService.updateDeliverySettings(settings);
        return ResponseEntity.ok(ApiResponse.ok("Delivery settings updated successfully", updated));
    }
}
