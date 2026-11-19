package com.novapetzlife.analytics;

import com.novapetzlife.analytics.dto.AnalyticsSummaryDto;
import com.novapetzlife.analytics.dto.LowStockAlertDto;
import com.novapetzlife.analytics.dto.TopProductDto;
import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Business Analytics", description = "Store revenue reporting, order status breakdowns, low stock alerts, and top-selling pet supplies")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get executive business KPIs", description = "Aggregated revenue, order volumes, average order value, payment distribution, and radius statistics")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> getSummary() {
        AnalyticsSummaryDto summary = analyticsService.getAnalyticsSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock products", description = "Catalog products that are at or below minimum threshold for store reordering")
    public ResponseEntity<ApiResponse<List<LowStockAlertDto>>> getLowStockAlerts() {
        List<LowStockAlertDto> alerts = analyticsService.getLowStockAlerts();
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    @GetMapping("/top-products")
    @Operation(summary = "Get top selling products", description = "Best performing items ranked by units sold and generated revenue")
    public ResponseEntity<ApiResponse<List<TopProductDto>>> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
        List<TopProductDto> top = analyticsService.getTopSellingProducts(limit);
        return ResponseEntity.ok(ApiResponse.ok(top));
    }
}
