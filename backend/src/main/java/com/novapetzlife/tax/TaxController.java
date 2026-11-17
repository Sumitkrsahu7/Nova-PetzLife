package com.novapetzlife.tax;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tax")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Tax & GST Engine", description = "Indian GST tax configuration, HSN codes, and active status toggles")
public class TaxController {

    private final TaxConfigurationRepository taxConfigRepository;

    public TaxController(TaxConfigurationRepository taxConfigRepository) {
        this.taxConfigRepository = taxConfigRepository;
    }

    @GetMapping("/configurations")
    @Operation(summary = "Get all GST tax configurations")
    public ResponseEntity<ApiResponse<List<TaxConfiguration>>> getAllConfigurations() {
        List<TaxConfiguration> configs = taxConfigRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @RequestMapping(value = {"/configurations/{id}/status", "/configurations/{id}/toggle-status"}, method = {RequestMethod.PATCH, RequestMethod.DELETE})
    @Operation(summary = "Toggle tax configuration active status")
    public ResponseEntity<ApiResponse<TaxConfiguration>> toggleTaxStatus(@PathVariable Long id) {
        TaxConfiguration config = taxConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tax configuration not found with id: " + id));

        config.setIsActive(!Boolean.TRUE.equals(config.getIsActive()));
        TaxConfiguration saved = taxConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.ok("Tax configuration status updated successfully", saved));
    }
}
