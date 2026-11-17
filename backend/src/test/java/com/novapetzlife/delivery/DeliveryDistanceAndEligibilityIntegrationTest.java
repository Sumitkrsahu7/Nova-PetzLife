package com.novapetzlife.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryDistanceAndEligibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @BeforeEach
    void setUp() {
        // Ensure shop location exists and is verified for testing delivery radius calculations
        ShopLocation shop = shopLocationRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            ShopLocation s = new ShopLocation(
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
            );
            return shopLocationRepository.save(s);
        });
        shop.setIsVerified(true);
        shopLocationRepository.save(shop);
    }

    @Test
    @DisplayName("Delivery Eligibility: Pattom Junction (1.1 km away) is within 20 km radius")
    void testNearbyLocationEligible() throws Exception {
        // Pattom coordinates: 8.5244, 76.9442
        DeliveryCheckRequest request = new DeliveryCheckRequest(8.5244, 76.9442, "695004", new BigDecimal("600.00"));

        mockMvc.perform(post("/api/v1/delivery/check-eligibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isEligible").value(true))
                .andExpect(jsonPath("$.data.distanceKm", lessThan(2.0)))
                .andExpect(jsonPath("$.data.deliveryCharge").value(0.00)); // Free delivery over ₹500
    }

    @Test
    @DisplayName("Delivery Eligibility: Kazhakkoottam Technopark (8.6 km away) is within 20 km radius")
    void testTechnoparkEligible() throws Exception {
        // Kazhakkoottam coordinates: 8.5583, 76.8812
        DeliveryCheckRequest request = new DeliveryCheckRequest(8.5583, 76.8812, "695581", new BigDecimal("350.00"));

        mockMvc.perform(post("/api/v1/delivery/check-eligibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isEligible").value(true))
                .andExpect(jsonPath("$.data.distanceKm", greaterThan(5.0)))
                .andExpect(jsonPath("$.data.distanceKm", lessThan(15.0)))
                .andExpect(jsonPath("$.data.deliveryCharge").value(50.00)); // Under ₹500 threshold
    }

    @Test
    @DisplayName("Delivery Eligibility: Kollam City (~55 km away) strictly exceeds 20 km radius")
    void testKollamExceedsRadius() throws Exception {
        // Kollam coordinates: 8.8932, 76.6141
        DeliveryCheckRequest request = new DeliveryCheckRequest(8.8932, 76.6141, "691001", new BigDecimal("1500.00"));

        mockMvc.perform(post("/api/v1/delivery/check-eligibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isEligible").value(false))
                .andExpect(jsonPath("$.data.distanceKm", greaterThan(40.0)))
                .andExpect(jsonPath("$.data.message", containsString("exceeds our strict 20.00 km store delivery radius")));
    }

    @Test
    @DisplayName("Delivery Slots: System returns active delivery windows (Morning, Afternoon, Evening)")
    void testGetActiveDeliverySlots() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/slots")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].slotName", hasItem(containsString("Morning"))));
    }

    @Test
    @DisplayName("Shop Information: Returns verified Kuravankonam coordinates and 20 km delivery radius")
    void testGetShopInfo() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/shop-info")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopName").value("NOVA PETZLIFE"))
                .andExpect(jsonPath("$.data.addressLine", containsString("Kuravankonam")))
                .andExpect(jsonPath("$.data.pinCode").value("695003"))
                .andExpect(jsonPath("$.data.deliveryRadiusKm").value(20.00));
    }
}
