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
        String email = getSetting("store_email", "support@novapetzlife.in");
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
}
