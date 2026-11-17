package com.novapetzlife.delivery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DeliveryService {

    private final ShopLocationRepository shopLocationRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final DeliverySlotRepository deliverySlotRepository;
    private final HaversineDistanceCalculator distanceCalculator;
    private final com.novapetzlife.audit.AuditService auditService;

    public DeliveryService(
            ShopLocationRepository shopLocationRepository,
            DeliverySettingsRepository deliverySettingsRepository,
            DeliverySlotRepository deliverySlotRepository,
            HaversineDistanceCalculator distanceCalculator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.audit.AuditService auditService) {
        this.shopLocationRepository = shopLocationRepository;
        this.deliverySettingsRepository = deliverySettingsRepository;
        this.deliverySlotRepository = deliverySlotRepository;
        this.distanceCalculator = distanceCalculator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ShopLocation getShopLocation() {
        return shopLocationRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> new ShopLocation(
                        "NOVA PETZLIFE",
                        "Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road",
                        "Thiruvananthapuram",
                        "Kerala",
                        "India",
                        "695003",
                        new BigDecimal("8.5284480"),
                        new BigDecimal("76.9532680"),
                        new BigDecimal("20.00"),
                        false
                ));
    }

    @Transactional(readOnly = true)
    public DeliverySettings getDeliverySettings() {
        return deliverySettingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> new DeliverySettings(
                        new BigDecimal("50.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("100.00"),
                        60,
                        true
                ));
    }

    @Transactional(readOnly = true)
    public DeliveryEligibilityResponse checkDeliveryEligibility(Double destLat, Double destLon, BigDecimal orderSubtotal) {
        if (destLat == null || destLon == null) {
            throw new IllegalArgumentException("Destination latitude and longitude are required for delivery check");
        }

        ShopLocation shop = getShopLocation();
        DeliverySettings settings = getDeliverySettings();

        if (!Boolean.TRUE.equals(settings.getIsDeliveryEnabled())) {
            return new DeliveryEligibilityResponse(
                    false,
                    BigDecimal.ZERO,
                    shop.getDeliveryRadiusKm(),
                    BigDecimal.ZERO,
                    settings.getFreeDeliveryThreshold(),
                    settings.getMinOrderValue(),
                    shop.getIsVerified(),
                    shop.getShopName(),
                    shop.getAddressLine(),
                    "Home delivery service is temporarily paused by store management."
            );
        }

        BigDecimal distanceKm = distanceCalculator.calculateDistanceKm(
                shop.getLatitude().doubleValue(),
                shop.getLongitude().doubleValue(),
                destLat,
                destLon
        );

        boolean withinRadius = distanceKm.compareTo(shop.getDeliveryRadiusKm()) <= 0;
        BigDecimal subtotal = orderSubtotal != null ? orderSubtotal : BigDecimal.ZERO;

        BigDecimal deliveryCharge = BigDecimal.ZERO;
        if (settings.getFreeDeliveryThreshold() != null && subtotal.compareTo(settings.getFreeDeliveryThreshold()) >= 0) {
            deliveryCharge = BigDecimal.ZERO; // Free Delivery threshold met!
        } else {
            deliveryCharge = settings.getDeliveryCharge();
        }

        String message;
        boolean eligible;

        if (!withinRadius) {
            eligible = false;
            message = String.format("Location is %.2f km away, which exceeds our strict 20.00 km store delivery radius from Kuravankonam.", distanceKm);
        } else if (!Boolean.TRUE.equals(shop.getIsVerified())) {
            eligible = false;
            message = "Store delivery coordinates are currently pending owner verification in the admin console.";
        } else {
            eligible = true;
            if (deliveryCharge.compareTo(BigDecimal.ZERO) == 0) {
                message = String.format("Eligible for FREE Delivery! Destination is %.2f km from our Kuravankonam store.", distanceKm);
            } else {
                BigDecimal remainingForFree = settings.getFreeDeliveryThreshold().subtract(subtotal);
                message = String.format("Eligible for delivery (₹%.2f charge). Add ₹%.2f more for FREE Delivery!", deliveryCharge, remainingForFree);
            }
        }

        return new DeliveryEligibilityResponse(
                eligible,
                distanceKm,
                shop.getDeliveryRadiusKm(),
                deliveryCharge,
                settings.getFreeDeliveryThreshold(),
                settings.getMinOrderValue(),
                shop.getIsVerified(),
                shop.getShopName(),
                shop.getAddressLine(),
                message
        );
    }

    @Transactional(readOnly = true)
    public List<DeliverySlotDto> getActiveDeliverySlots() {
        return deliverySlotRepository.findByIsActiveTrueOrderByStartTimeAsc().stream()
                .map(DeliverySlotDto::fromEntity)
                .toList();
    }

    @Transactional
    public ShopLocationDto verifyShopCoordinates(boolean verified) {
        ShopLocation shop = shopLocationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Shop location record not found"));

        shop.setIsVerified(verified);
        ShopLocation saved = shopLocationRepository.save(shop);
        if (auditService != null) {
            auditService.recordAudit("Delivery", "SHOP_LOCATION", "SHOP_LOCATION_VERIFIED", "OWNER", String.valueOf(!verified), String.valueOf(verified), null);
        }
        return ShopLocationDto.fromEntity(saved);
    }

    @Transactional
    public ShopLocationDto updateShopLocation(ShopLocationUpdateRequest request) {
        ShopLocation shop = shopLocationRepository.findFirstByOrderByIdAsc()
                .orElseGet(ShopLocation::new);

        shop.setShopName(request.shopName().trim());
        shop.setAddressLine(request.addressLine().trim());
        shop.setCity(request.city().trim());
        shop.setState(request.state().trim());
        shop.setPinCode(request.pinCode().trim());
        shop.setGooglePlaceId(request.googlePlaceId());
        shop.setLatitude(request.latitude());
        shop.setLongitude(request.longitude());
        shop.setDeliveryRadiusKm(request.deliveryRadiusKm());
        shop.setDirectionsUrl(request.directionsUrl());
        shop.setIsVerified(false); // Reset to require explicit owner verification upon address edit

        ShopLocation saved = shopLocationRepository.save(shop);
        if (auditService != null) {
            auditService.recordAudit("Delivery", "SHOP_LOCATION", "SHOP_LOCATION_UPDATED", "ADMIN", null, saved.getAddressLine(), null);
        }
        return ShopLocationDto.fromEntity(saved);
    }

    @Transactional
    public DeliverySettings updateDeliverySettings(DeliverySettings updated) {
        DeliverySettings settings = deliverySettingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(DeliverySettings::new);

        if (updated.getDeliveryCharge() != null) settings.setDeliveryCharge(updated.getDeliveryCharge());
        if (updated.getFreeDeliveryThreshold() != null) settings.setFreeDeliveryThreshold(updated.getFreeDeliveryThreshold());
        if (updated.getMinOrderValue() != null) settings.setMinOrderValue(updated.getMinOrderValue());
        if (updated.getPrepTimeMinutes() != null) settings.setPrepTimeMinutes(updated.getPrepTimeMinutes());
        if (updated.getIsDeliveryEnabled() != null) settings.setIsDeliveryEnabled(updated.getIsDeliveryEnabled());

        return deliverySettingsRepository.save(settings);
    }
}
