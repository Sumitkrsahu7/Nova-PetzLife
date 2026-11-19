package com.novapetzlife.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/health", "/api/v1/health"})
@Tag(name = "Health", description = "System health and status endpoints")
public class HealthController {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private final com.novapetzlife.delivery.ShopLocationRepository shopLocationRepository;

    public HealthController(@org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.delivery.ShopLocationRepository shopLocationRepository) {
        this.shopLocationRepository = shopLocationRepository;
    }

    @GetMapping
    @Operation(summary = "Check backend service health", description = "Returns the status of NOVA PETZLIFE backend API")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("service", "NOVA PETZLIFE REST API");
        status.put("version", appVersion);
        status.put("profile", activeProfile);

        String shopAddr = "Thiruvananthapuram, Kerala, India";
        if (shopLocationRepository != null) {
            shopAddr = shopLocationRepository.findByIsActiveTrue()
                    .map(com.novapetzlife.delivery.ShopLocation::getAddressLine)
                    .orElse(shopAddr);
        }
        status.put("shopLocation", shopAddr);
        status.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.ok("NOVA PETZLIFE backend is operational", status));
    }
}
