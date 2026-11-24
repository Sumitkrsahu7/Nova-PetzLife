package com.novapetzlife.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteSettingService {

    private final WebsiteSettingRepository settingRepository;

    public WebsiteSettingService(WebsiteSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(WebsiteSetting::getSettingValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllSettings() {
        List<WebsiteSetting> list = settingRepository.findAll();
        Map<String, String> map = new HashMap<>();
        for (WebsiteSetting s : list) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    @Transactional
    public WebsiteSetting updateSetting(String key, String value, String description) {
        WebsiteSetting setting = settingRepository.findBySettingKey(key)
                .orElse(new WebsiteSetting(key, value, description));
        setting.setSettingValue(value);
        if (description != null) {
            setting.setDescription(description);
        }
        setting.setUpdatedAt(Instant.now());
        return settingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public ContactInfoDto getContactInfo() {
        String whatsapp = getSetting("store_whatsapp", "+919447100000");
        String defaultMsg = getSetting("store_whatsapp_default_msg", "Hello Nova Petzlife, I would like to inquire about your pet products and delivery.");
        String address = getSetting("store_address", "Nova Petzlife, Kowdiar / Kuravankonam, Thiruvananthapuram, Kerala 695003");
        String phone = getSetting("store_phone", "+91 94471 00000");
        String email = getSetting("store_email", "support@novapetzlife.com");
        String timings = getSetting("store_timings", "Monday – Saturday: 9:00 AM – 8:30 PM | Sunday: 10:00 AM – 7:00 PM");

        // Format clean phone for wa.me URL (remove plus, spaces, dashes)
        String cleanNumber = whatsapp.replaceAll("[^0-9]", "");
        String encodedMsg = java.net.URLEncoder.encode(defaultMsg, java.nio.charset.StandardCharsets.UTF_8);
        String whatsappUrl = "https://wa.me/" + cleanNumber + "?text=" + encodedMsg;

        return new ContactInfoDto(
                whatsapp,
                whatsappUrl,
                defaultMsg,
                phone,
                email,
                address,
                timings
        );
    }

    public record ContactInfoDto(
            String whatsappNumber,
            String whatsappUrl,
            String defaultMessage,
            String phone,
            String email,
            String address,
            String timings
    ) {}

    public record StoreStatusDto(
            boolean isShopOpen,
            String shopOpenTime,
            String shopCloseTime,
            String sundayOpenTime,
            String sundayCloseTime,
            boolean isDeliveryActive,
            String deliveryStartTime,
            String deliveryEndTime,
            String deliverySpeedNotice,
            String storeTimingsSummary
    ) {}

    @Transactional(readOnly = true)
    public StoreStatusDto getStoreStatus() {
        boolean isShopOpen = Boolean.parseBoolean(getSetting("store_is_open", "true"));
        String shopOpenTime = getSetting("store_open_time", "09:00 AM");
        String shopCloseTime = getSetting("store_close_time", "08:30 PM");
        String sundayOpenTime = getSetting("store_sunday_open_time", "10:00 AM");
        String sundayCloseTime = getSetting("store_sunday_close_time", "06:00 PM");

        boolean isDeliveryActive = Boolean.parseBoolean(getSetting("delivery_is_active", "true"));
        String deliveryStartTime = getSetting("delivery_start_time", "09:30 AM");
        String deliveryEndTime = getSetting("delivery_end_time", "08:00 PM");
        String deliverySpeedNotice = getSetting("delivery_speed_notice", "Express Delivery within 2 - 3 Hours in Trivandrum");
        String storeTimingsSummary = getSetting("store_timings", "Mon - Sat: 09:00 AM - 08:30 PM | Sun: 10:00 AM - 06:00 PM");

        return new StoreStatusDto(
                isShopOpen,
                shopOpenTime,
                shopCloseTime,
                sundayOpenTime,
                sundayCloseTime,
                isDeliveryActive,
                deliveryStartTime,
                deliveryEndTime,
                deliverySpeedNotice,
                storeTimingsSummary
        );
    }

    @Transactional
    public StoreStatusDto updateStoreStatus(StoreStatusDto dto) {
        updateSetting("store_is_open", String.valueOf(dto.isShopOpen()), "Whether the physical pet shop is currently open for customers");
        if (dto.shopOpenTime() != null) {
            updateSetting("store_open_time", dto.shopOpenTime(), "Daily shop opening time");
        }
        if (dto.shopCloseTime() != null) {
            updateSetting("store_close_time", dto.shopCloseTime(), "Daily shop closing time");
        }
        if (dto.sundayOpenTime() != null) {
            updateSetting("store_sunday_open_time", dto.sundayOpenTime(), "Sunday opening time");
        }
        if (dto.sundayCloseTime() != null) {
            updateSetting("store_sunday_close_time", dto.sundayCloseTime(), "Sunday closing time");
        }
        updateSetting("delivery_is_active", String.valueOf(dto.isDeliveryActive()), "Whether local express delivery service is active");
        if (dto.deliveryStartTime() != null) {
            updateSetting("delivery_start_time", dto.deliveryStartTime(), "Daily delivery operations start time");
        }
        if (dto.deliveryEndTime() != null) {
            updateSetting("delivery_end_time", dto.deliveryEndTime(), "Daily delivery operations end time");
        }
        if (dto.deliverySpeedNotice() != null) {
            updateSetting("delivery_speed_notice", dto.deliverySpeedNotice(), "Delivery turnaround notice");
        }
        if (dto.storeTimingsSummary() != null) {
            updateSetting("store_timings", dto.storeTimingsSummary(), "Display summary of store timings");
        }
        return getStoreStatus();
    }
}
