package com.novapetzlife.delivery;

import com.novapetzlife.audit.AuditService;
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
    private final AuditService auditService;

    public DeliveryService(
            ShopLocationRepository shopLocationRepository,
            DeliverySettingsRepository deliverySettingsRepository,
            DeliverySlotRepository deliverySlotRepository,
            HaversineDistanceCalculator distanceCalculator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService
    ) {
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
                        true
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

        BigDecimal deliveryRadius = shop.getDeliveryRadiusKm() != null ? shop.getDeliveryRadiusKm() : new BigDecimal("20.00");
        boolean withinRadius = distanceKm.compareTo(deliveryRadius) <= 0;
        BigDecimal subtotal = orderSubtotal != null ? orderSubtotal : BigDecimal.ZERO;

        BigDecimal deliveryCharge = BigDecimal.ZERO;
        if (settings.getFreeDeliveryThreshold() != null && subtotal.compareTo(settings.getFreeDeliveryThreshold()) >= 0) {
            deliveryCharge = BigDecimal.ZERO; // Free Delivery threshold met!
        } else {
            deliveryCharge = settings.getDeliveryCharge();
        }

        BigDecimal estimatedRoadDistanceKm = distanceCalculator.calculateEstimatedRoadDistanceKm(distanceKm.doubleValue());
        int estimatedDurationMinutes = distanceCalculator.calculateEstimatedDurationMinutes(estimatedRoadDistanceKm.doubleValue());

        String message;
        boolean eligible;

        if (!withinRadius) {
            eligible = false;
            message = String.format("Location is %.2f km away, which exceeds our strict 20.00 km store delivery radius from Kuravankonam.", distanceKm);
        } else {
            eligible = true;
            if (deliveryCharge.compareTo(BigDecimal.ZERO) == 0) {
                message = String.format("Eligible for FREE Delivery! Destination is %.2f km from our Kuravankonam store (est. road route ~%.2f km, approx %d mins transit).",
                        distanceKm, estimatedRoadDistanceKm, estimatedDurationMinutes);
            } else {
                BigDecimal remainingForFree = settings.getFreeDeliveryThreshold() != null
                        ? settings.getFreeDeliveryThreshold().subtract(subtotal)
                        : BigDecimal.ZERO;
                if (remainingForFree.compareTo(BigDecimal.ZERO) > 0) {
                    message = String.format("Eligible for delivery (₹%.2f charge). Add ₹%.2f more for FREE Delivery! Destination is %.2f km away (est. road route ~%.2f km, approx %d mins transit).",
                            deliveryCharge, remainingForFree, distanceKm, estimatedRoadDistanceKm, estimatedDurationMinutes);
                } else {
                    message = String.format("Eligible for delivery (₹%.2f charge). Destination is %.2f km away (est. road route ~%.2f km, approx %d mins transit).",
                            deliveryCharge, distanceKm, estimatedRoadDistanceKm, estimatedDurationMinutes);
                }
            }
        }

        return new DeliveryEligibilityResponse(
                eligible,
                distanceKm,
                deliveryRadius,
                deliveryCharge,
                settings.getFreeDeliveryThreshold(),
                settings.getMinOrderValue(),
                Boolean.TRUE.equals(shop.getIsVerified()),
                shop.getShopName(),
                shop.getAddressLine(),
                message,
                estimatedRoadDistanceKm,
                estimatedDurationMinutes
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
        shop.setIsVerified(true); // Confirmed coordinates active for delivery

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
