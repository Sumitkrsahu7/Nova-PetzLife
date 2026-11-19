package com.novapetzlife.settings;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Store & Website Settings", description = "Public store contact info, WhatsApp configuration, and admin website settings")
public class StoreSettingsController {

    private final WebsiteSettingService settingService;

    public StoreSettingsController(WebsiteSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/settings/contact")
    @Operation(summary = "Get official store contact and WhatsApp info", description = "Returns dynamic WhatsApp number, wa.me chat link, address, and operating hours")
    public ResponseEntity<ApiResponse<WebsiteSettingService.ContactInfoDto>> getContactInfo() {
        return ResponseEntity.ok(ApiResponse.ok(settingService.getContactInfo()));
    }

    @GetMapping("/settings/store-status")
    @Operation(summary = "Get live store opening hours & delivery status", description = "Returns whether the shop is open, operating hours, and active delivery windows")
    public ResponseEntity<ApiResponse<WebsiteSettingService.StoreStatusDto>> getStoreStatus() {
        return ResponseEntity.ok(ApiResponse.ok(settingService.getStoreStatus()));
    }

    @PutMapping("/admin/settings/store-status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Update shop opening/closing hours and delivery times (Super Admin)", description = "Allows Super Admin to toggle shop open/closed, change opening hours, and delivery operational timings")
    public ResponseEntity<ApiResponse<WebsiteSettingService.StoreStatusDto>> updateStoreStatus(@RequestBody WebsiteSettingService.StoreStatusDto dto) {
        WebsiteSettingService.StoreStatusDto updated = settingService.updateStoreStatus(dto);
        return ResponseEntity.ok(ApiResponse.ok("Store hours and delivery status updated successfully", updated));
    }

    @GetMapping("/admin/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Get all store settings (Admin)", description = "Returns all dynamic configuration key-value pairs")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAllSettings() {
        return ResponseEntity.ok(ApiResponse.ok(settingService.getAllSettings()));
    }

    @PutMapping("/admin/settings/{key}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'STORE_SETTINGS_EDIT')")
    @Operation(summary = "Update store setting (Admin)", description = "Updates a setting value such as WhatsApp number, store address, etc.")
    public ResponseEntity<ApiResponse<WebsiteSetting>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.getOrDefault("value", "");
        String description = body.get("description");
        WebsiteSetting updated = settingService.updateSetting(key, value, description);
        return ResponseEntity.ok(ApiResponse.ok("Setting updated successfully", updated));
    }
}
