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

    @PutMapping("/configurations/{id}")
    @Operation(summary = "Update GST tax configuration")
    public ResponseEntity<ApiResponse<TaxConfiguration>> updateTaxConfiguration(
            @PathVariable Long id,
            @RequestBody TaxConfiguration request) {
        TaxConfiguration config = taxConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tax configuration not found with id: " + id));

        if (request.getTaxName() != null && !request.getTaxName().isBlank()) {
            config.setTaxName(request.getTaxName().trim());
        }
        if (request.getHsnCode() != null && !request.getHsnCode().isBlank()) {
            config.setHsnCode(request.getHsnCode().trim());
        }
        if (request.getCgstRate() != null) {
            config.setCgstRate(request.getCgstRate());
        }
        if (request.getSgstRate() != null) {
            config.setSgstRate(request.getSgstRate());
        }
        if (request.getIgstRate() != null) {
            config.setIgstRate(request.getIgstRate());
        } else if (request.getCgstRate() != null && request.getSgstRate() != null) {
            config.setIgstRate(request.getCgstRate().add(request.getSgstRate()));
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }

        TaxConfiguration saved = taxConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.ok("GST configuration updated successfully", saved));
    }

    @PostMapping("/configurations")
    @Operation(summary = "Create a new GST tax configuration")
    public ResponseEntity<ApiResponse<TaxConfiguration>> createTaxConfiguration(
            @RequestBody TaxConfiguration request) {
        if (request.getTaxName() == null || request.getTaxName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("Tax description name is required"));
        }
        if (request.getHsnCode() == null || request.getHsnCode().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("HSN code is required"));
        }
        if (request.getCgstRate() == null) request.setCgstRate(java.math.BigDecimal.ZERO);
        if (request.getSgstRate() == null) request.setSgstRate(java.math.BigDecimal.ZERO);
        if (request.getIgstRate() == null) {
            request.setIgstRate(request.getCgstRate().add(request.getSgstRate()));
        }
        if (request.getIsActive() == null) request.setIsActive(true);

        TaxConfiguration saved = taxConfigRepository.save(request);
        return ResponseEntity.ok(ApiResponse.ok("GST configuration created successfully", saved));
    }
}
