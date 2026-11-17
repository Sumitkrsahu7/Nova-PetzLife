package com.novapetzlife.brand;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Brands", description = "Brand catalog management with strict brand isolation")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping("/brands")
    @Operation(summary = "Get list of active brands")
    public ResponseEntity<ApiResponse<List<BrandDto>>> getAllBrands() {
        List<BrandDto> brands = brandService.getAllActiveBrands();
        return ResponseEntity.ok(ApiResponse.ok(brands));
    }

    @GetMapping("/brands/{slug}")
    @Operation(summary = "Get brand details by slug")
    public ResponseEntity<ApiResponse<BrandDto>> getBrandBySlug(@PathVariable String slug) {
        BrandDto brand = brandService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.ok(brand));
    }

    @PostMapping("/admin/brands")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Create brand (Admin only)")
    public ResponseEntity<ApiResponse<BrandDto>> createBrand(@Valid @RequestBody BrandCreateRequest request) {
        BrandDto created = brandService.createBrand(request);
        return ResponseEntity.ok(ApiResponse.ok("Brand created successfully", created));
    }

    @PutMapping("/admin/brands/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Update brand (Admin only)")
    public ResponseEntity<ApiResponse<BrandDto>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandCreateRequest request) {
        BrandDto updated = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Brand updated successfully", updated));
    }

    @RequestMapping(value = {"/admin/brands/{id}/status", "/admin/brands/{id}/toggle-status"}, method = {RequestMethod.PATCH, RequestMethod.DELETE})
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Toggle brand active state (Admin only)")
    public ResponseEntity<ApiResponse<Void>> toggleBrandStatus(@PathVariable Long id) {
        brandService.toggleActiveStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Brand status toggled successfully", null));
    }
}
