package com.novapetzlife.inventory;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Inventory Operations", description = "Stock control, pessimistic concurrency, low-stock alerts, and batch expiry monitoring")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "Get all inventory items", description = "Retrieve catalog inventory records with live stock and thresholds")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getAllInventory() {
        List<InventoryDto> items = inventoryService.getAllInventory().stream()
                .map(InventoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock items", description = "Retrieve all catalog items where available stock is below configured minimum threshold")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getLowStockAlerts() {
        List<InventoryDto> lowStock = inventoryService.getLowStockAlerts().stream()
                .map(InventoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lowStock));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get inventory for product", description = "Retrieve inventory records for a product and its variants")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getProductInventory(@PathVariable Long productId) {
        List<InventoryDto> items = inventoryService.getInventoryByProductId(productId).stream()
                .map(InventoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @PostMapping("/adjust")
    @Operation(summary = "Adjust inventory stock", description = "Perform manual stock adjustment (positive or negative) with audit logging")
    public ResponseEntity<ApiResponse<Void>> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        inventoryService.adjustStock(
                request.productId(),
                request.variantId(),
                request.quantityAdjustment(),
                request.reason()
        );
        return ResponseEntity.ok(ApiResponse.ok("Inventory adjusted successfully", null));
    }

    @GetMapping("/near-expiry")
    @Operation(summary = "Get near-expiry batches", description = "Find inventory batches with expiry dates within specified threshold window")
    public ResponseEntity<ApiResponse<List<InventoryBatch>>> getNearExpiryBatches(@RequestParam(defaultValue = "30") int daysThreshold) {
        List<InventoryBatch> batches = inventoryService.getNearExpiryBatches(daysThreshold);
        return ResponseEntity.ok(ApiResponse.ok(batches));
    }
}
