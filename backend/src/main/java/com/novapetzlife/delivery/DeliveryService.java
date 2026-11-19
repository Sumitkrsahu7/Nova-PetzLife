package com.novapetzlife.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final ShopLocationRepository shopLocationRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final DeliverySlotRepository deliverySlotRepository;
    private final HaversineDistanceCalculator distanceCalculator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeliveryService(
            ShopLocationRepository shopLocationRepository,
            DeliverySettingsRepository deliverySettingsRepository,
            DeliverySlotRepository deliverySlotRepository,
            HaversineDistanceCalculator distanceCalculator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.shopLocationRepository = shopLocationRepository;
        this.deliverySettingsRepository = deliverySettingsRepository;
        this.deliverySlotRepository = deliverySlotRepository;
        this.distanceCalculator = distanceCalculator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    @Transactional(readOnly = true)
    public ShopLocation getShopLocation() {
        return shopLocationRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    ShopLocation unconfigured = new ShopLocation();
                    unconfigured.setShopName("NOVA PETZLIFE");
                    unconfigured.setAddressLine("Store Location Pending Configuration");
                    unconfigured.setCity("Thiruvananthapuram");
                    unconfigured.setState("Kerala");
                    unconfigured.setCountry("India");
                    unconfigured.setPinCode("695003");
                    unconfigured.setLatitude(new BigDecimal("8.5241000"));
                    unconfigured.setLongitude(new BigDecimal("76.9530000"));
                    unconfigured.setDeliveryRadiusKm(new BigDecimal("20.00"));
                    unconfigured.setIsVerified(false);
                    return unconfigured;
                });
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

        // 1. Verify if store location is configured and verified by store admin
        if (!Boolean.TRUE.equals(shop.getIsVerified()) || shop.getLatitude() == null || shop.getLongitude() == null) {
            return new DeliveryEligibilityResponse(
                    false,
                    BigDecimal.ZERO,
                    shop.getDeliveryRadiusKm() != null ? shop.getDeliveryRadiusKm() : new BigDecimal("20.00"),
                    BigDecimal.ZERO,
                    settings.getFreeDeliveryThreshold(),
                    settings.getMinOrderValue(),
                    false,
                    shop.getShopName(),
                    shop.getAddressLine(),
                    "Store delivery coordinates are currently pending owner verification in the admin console. Please contact store support.",
                    BigDecimal.ZERO,
                    0
            );
        }

        // 2. Verify if delivery service is enabled
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
                    "Home delivery service is temporarily paused by store management.",
                    BigDecimal.ZERO,
                    0
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

        BigDecimal deliveryCharge;
        if (settings.getFreeDeliveryThreshold() != null && subtotal.compareTo(settings.getFreeDeliveryThreshold()) >= 0) {
            deliveryCharge = BigDecimal.ZERO;
        } else {
            deliveryCharge = settings.getDeliveryCharge() != null ? settings.getDeliveryCharge() : new BigDecimal("50.00");
        }

        BigDecimal estimatedRoadDistanceKm = distanceCalculator.calculateEstimatedRoadDistanceKm(distanceKm.doubleValue());
        int estimatedDurationMinutes = distanceCalculator.calculateEstimatedDurationMinutes(estimatedRoadDistanceKm.doubleValue());

        String message;
        boolean eligible;

        if (!withinRadius) {
            eligible = false;
            message = String.format("Sorry, delivery is currently unavailable at this location. Our delivery service is available within a %s km radius of our store (your location is %.1f km away).",
                    deliveryRadius.stripTrailingZeros().toPlainString(), distanceKm);
        } else {
            eligible = true;
            if (deliveryCharge.compareTo(BigDecimal.ZERO) == 0) {
                message = String.format("Great! Delivery is available at your location. Destination is %.1f km from our store (est. road route ~%.1f km, approx %d mins transit).",
                        distanceKm, estimatedRoadDistanceKm, estimatedDurationMinutes);
            } else {
                BigDecimal remainingForFree = settings.getFreeDeliveryThreshold() != null
                        ? settings.getFreeDeliveryThreshold().subtract(subtotal)
                        : BigDecimal.ZERO;
                if (remainingForFree.compareTo(BigDecimal.ZERO) > 0) {
                    message = String.format("Great! Delivery is available at your location (₹%.2f delivery charge). Add ₹%.2f more for FREE Delivery! Distance: %.1f km.",
                            deliveryCharge, remainingForFree, distanceKm);
                } else {
                    message = String.format("Great! Delivery is available at your location (₹%.2f delivery charge). Distance: %.1f km from our store.",
                            deliveryCharge, distanceKm);
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

    /**
     * Resolves a customer's submitted address and evaluates 20 km radius eligibility.
     */
    @Transactional(readOnly = true)
    public DeliveryEligibilityResponse checkAddressDeliveryEligibility(AddressDeliveryCheckRequest req) {
        Double lat = req.latitude();
        Double lon = req.longitude();

        // If coordinates not directly provided, geocode the address
        if (lat == null || lon == null || lat == 0.0 || lon == 0.0) {
            List<String> parts = new ArrayList<>();
            if (req.fullAddress() != null && !req.fullAddress().isBlank()) {
                parts.add(req.fullAddress().trim());
            } else {
                if (req.buildingOrHouse() != null && !req.buildingOrHouse().isBlank()) parts.add(req.buildingOrHouse().trim());
                if (req.streetArea() != null && !req.streetArea().isBlank()) parts.add(req.streetArea().trim());
                if (req.landmark() != null && !req.landmark().isBlank()) parts.add(req.landmark().trim());
                if (req.city() != null && !req.city().isBlank()) parts.add(req.city().trim());
                if (req.state() != null && !req.state().isBlank()) parts.add(req.state().trim());
                if (req.pincode() != null && !req.pincode().isBlank()) parts.add(req.pincode().trim());
            }

            if (parts.isEmpty()) {
                return new DeliveryEligibilityResponse(
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        "NOVA PETZLIFE",
                        "",
                        "Please provide a complete address with street, locality, or PIN code to verify delivery availability.",
                        BigDecimal.ZERO,
                        0
                );
            }

            String queryAddress = String.join(", ", parts);
            double[] resolvedCoords = geocodeAddress(queryAddress);
            if (resolvedCoords == null) {
                return new DeliveryEligibilityResponse(
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        "NOVA PETZLIFE",
                        "",
                        "We could not pinpoint this exact address. Please provide a more complete address including your street, landmark, or PIN code.",
                        BigDecimal.ZERO,
                        0
                );
            }

            lat = resolvedCoords[0];
            lon = resolvedCoords[1];
        }

        return checkDeliveryEligibility(lat, lon, req.orderSubtotal());
    }

    private double[] geocodeAddress(String address) {
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + encoded;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "NovaPetzlife-DeliveryService/1.0 (support@novapetzlife.com)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode array = objectMapper.readTree(resp.body());
                if (array.isArray() && !array.isEmpty()) {
                    JsonNode first = array.get(0);
                    double lat = first.get("lat").asDouble();
                    double lon = first.get("lon").asDouble();
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception e) {
            log.warn("Address geocoding encountered non-fatal timeout or error for address '{}': {}", address, e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Delivery Windows / Slots Management
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DeliverySlotDto> getAllDeliverySlots() {
        return deliverySlotRepository.findAll().stream()
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .map(DeliverySlotDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliverySlotDto> getActiveDeliverySlots() {
        return deliverySlotRepository.findByIsActiveTrueOrderByStartTimeAsc().stream()
                .map(DeliverySlotDto::fromEntity)
                .toList();
    }

    @Transactional
    public DeliverySlotDto createDeliverySlot(DeliverySlotCreateRequest req) {
        if (req.startTime().isAfter(req.endTime()) || req.startTime().equals(req.endTime())) {
            throw new IllegalArgumentException("Start time must be strictly before end time.");
        }

        DeliverySlot slot = new DeliverySlot(
                req.slotName().trim(),
                req.startTime(),
                req.endTime(),
                req.maxCapacity() != null ? req.maxCapacity() : 20,
                req.isActive() != null ? req.isActive() : true,
                req.availableDays() != null && !req.availableDays().isBlank()
                        ? req.availableDays().trim()
                        : "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"
        );

        DeliverySlot saved = deliverySlotRepository.save(slot);
        if (auditService != null) {
            auditService.recordAudit("DeliverySlot", saved.getSlotName(), "DELIVERY_SLOT_CREATED", "ADMIN", null, saved.getSlotName(), null);
        }
        return DeliverySlotDto.fromEntity(saved);
    }

    @Transactional
    public DeliverySlotDto updateDeliverySlot(Long id, DeliverySlotCreateRequest req) {
        DeliverySlot slot = deliverySlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery slot not found with id: " + id));

        if (req.startTime().isAfter(req.endTime()) || req.startTime().equals(req.endTime())) {
            throw new IllegalArgumentException("Start time must be strictly before end time.");
        }

        slot.setSlotName(req.slotName().trim());
        slot.setStartTime(req.startTime());
        slot.setEndTime(req.endTime());
        if (req.maxCapacity() != null) slot.setMaxCapacity(req.maxCapacity());
        if (req.isActive() != null) slot.setIsActive(req.isActive());
        if (req.availableDays() != null && !req.availableDays().isBlank()) {
            slot.setAvailableDays(req.availableDays().trim());
        }

        DeliverySlot saved = deliverySlotRepository.save(slot);
        if (auditService != null) {
            auditService.recordAudit("DeliverySlot", saved.getSlotName(), "DELIVERY_SLOT_UPDATED", "ADMIN", null, saved.getSlotName(), null);
        }
        return DeliverySlotDto.fromEntity(saved);
    }

    @Transactional
    public DeliverySlotDto toggleDeliverySlotStatus(Long id) {
        DeliverySlot slot = deliverySlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery slot not found with id: " + id));

        slot.setIsActive(!Boolean.TRUE.equals(slot.getIsActive()));
        DeliverySlot saved = deliverySlotRepository.save(slot);
        if (auditService != null) {
            auditService.recordAudit("DeliverySlot", saved.getSlotName(), "DELIVERY_SLOT_TOGGLED", "ADMIN",
                    String.valueOf(!saved.getIsActive()), String.valueOf(saved.getIsActive()), null);
        }
        return DeliverySlotDto.fromEntity(saved);
    }

    @Transactional
    public void deleteDeliverySlot(Long id) {
        DeliverySlot slot = deliverySlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery slot not found with id: " + id));

        deliverySlotRepository.delete(slot);
        if (auditService != null) {
            auditService.recordAudit("DeliverySlot", slot.getSlotName(), "DELIVERY_SLOT_DELETED", "ADMIN", slot.getSlotName(), null, null);
        }
    }

    // -------------------------------------------------------------------------
    // Shop Location Management
    // -------------------------------------------------------------------------

    @Transactional
    public ShopLocationDto verifyShopCoordinates(boolean verified) {
        ShopLocation shop = shopLocationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Shop location record not found"));

        shop.setIsVerified(verified);
        if (verified) {
            shop.setVerifiedAt(Instant.now());
        }
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
        shop.setDeliveryRadiusKm(request.deliveryRadiusKm() != null ? request.deliveryRadiusKm() : new BigDecimal("20.00"));
        shop.setDirectionsUrl(request.directionsUrl());
        shop.setIsVerified(true);
        shop.setVerifiedAt(Instant.now());

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
