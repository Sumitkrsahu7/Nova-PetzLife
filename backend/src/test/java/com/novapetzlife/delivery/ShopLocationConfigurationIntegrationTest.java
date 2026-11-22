package com.novapetzlife.delivery;

import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.report.SalesReportOrderDto;
import com.novapetzlife.report.SalesReportService;
import com.novapetzlife.report.SalesReportSummaryDto;
import com.novapetzlife.support.dto.StoreInfoDto;
import com.novapetzlife.support.SupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ShopLocationConfigurationIntegrationTest {

    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private SupportService supportService;

    @Autowired
    private SalesReportService salesReportService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.novapetzlife.customer.CustomerRepository customerRepository;

    @Autowired
    private com.novapetzlife.user.UserRepository userRepository;

    @Autowired
    private com.novapetzlife.user.RoleRepository roleRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private ShopLocation originalShop;
    private com.novapetzlife.customer.Customer testCustomer;

    @BeforeEach
    void setUp() {
        originalShop = shopLocationRepository.findFirstByOrderByIdAsc().orElse(null);

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() -> {
            com.novapetzlife.user.Role role = roleRepository.findByName("ROLE_CUSTOMER")
                    .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_CUSTOMER", "Customer account")));
            String uuid = UUID.randomUUID().toString().substring(0, 6);
            com.novapetzlife.user.User u = new com.novapetzlife.user.User("cust_" + uuid + "@test.com", passwordEncoder.encode("Pass123!"), "Shop", "Tester", "98470" + uuid);
            u.setRoles(java.util.Set.of(role));
            u = userRepository.save(u);
            com.novapetzlife.customer.Customer c = new com.novapetzlife.customer.Customer(u, u.getEmail(), u.getPhone(), "Shop", "Tester", false);
            return customerRepository.save(c);
        });
    }

    @AfterEach
    void tearDown() {
        if (originalShop != null) {
            shopLocationRepository.deleteAll();
            originalShop.setId(null);
            shopLocationRepository.save(originalShop);
        }
    }

    @Test
    @DisplayName("Configured shop city and PIN code are used from database ShopLocation")
    void configuredShopCityAndPinCode_areUsed() {
        shopLocationRepository.deleteAll();
        ShopLocation customShop = new ShopLocation(
                "NOVA PETZLIFE KOCHI",
                "Panampilly Nagar Main Road",
                "Kochi",
                "Kerala",
                "India",
                "682036",
                new BigDecimal("9.9654000"),
                new BigDecimal("76.2925000"),
                new BigDecimal("25.00"),
                true
        );
        shopLocationRepository.save(customShop);

        ShopLocation retrieved = deliveryService.getShopLocation();
        assertEquals("Kochi", retrieved.getCity(), "DeliveryService must return the configured city");
        assertEquals("682036", retrieved.getPinCode(), "DeliveryService must return the configured PIN code");

        StoreInfoDto storeInfo = supportService.getStoreInfo();
        assertEquals("Kochi", storeInfo.getCity(), "SupportService must consume the configured city");
        assertEquals("682036", storeInfo.getPostalCode(), "SupportService must consume the configured PIN code");
    }

    @Test
    @DisplayName("Changing configured location propagates to consumers immediately")
    void changingConfiguredLocation_propagatesToConsumers() {
        ShopLocationUpdateRequest updateReq = new ShopLocationUpdateRequest(
                "NOVA PETZLIFE KOLLAM",
                "Beach Road, Kollam",
                "Kollam",
                "Kerala",
                "691001",
                "ChIJkollamPlaceId",
                new BigDecimal("8.8932000"),
                new BigDecimal("76.6141000"),
                new BigDecimal("15.00"),
                "https://maps.google.com/?q=kollam"
        );

        deliveryService.updateShopLocation(updateReq);

        ShopLocation updated = deliveryService.getShopLocation();
        assertEquals("Kollam", updated.getCity(), "Updated city must immediately propagate");
        assertEquals("691001", updated.getPinCode(), "Updated PIN code must immediately propagate");

        StoreInfoDto storeInfo = supportService.getStoreInfo();
        assertEquals("Kollam", storeInfo.getCity());
        assertEquals("691001", storeInfo.getPostalCode());
    }

    @Test
    @DisplayName("Missing shop configuration does not silently fall back to Thiruvananthapuram or 695003")
    void missingConfiguration_doesNotSilentlyUseHardcodedLocation() {
        shopLocationRepository.deleteAll();

        ShopLocation unconfigured = deliveryService.getShopLocation();
        assertNull(unconfigured.getCity(), "Missing configuration must not silently use Thiruvananthapuram");
        assertNull(unconfigured.getPinCode(), "Missing configuration must not silently use 695003");
        assertFalse(Boolean.TRUE.equals(unconfigured.getIsVerified()));

        // Delivery check with unconfigured shop must reject safely with sanitized message
        DeliveryEligibilityResponse resp = deliveryService.checkDeliveryEligibility(8.5244, 76.9442, new BigDecimal("500.00"));
        assertFalse(resp.isEligible(), "Delivery check must be ineligible when store location is unconfigured");
        assertNotNull(resp.message());
        assertTrue(resp.message().contains("pending configuration") || resp.message().contains("unavailable"),
                "Must return a clear sanitized error message");
    }

    @Test
    @DisplayName("Invalid PIN code configuration is rejected on update and delivery check")
    void invalidPinCodeConfiguration_isHandledSafely() {
        // Invalid PIN code: less than 6 digits
        ShopLocationUpdateRequest invalidReq = new ShopLocationUpdateRequest(
                "NOVA PETZLIFE",
                "Some Address",
                "Alappuzha",
                "Kerala",
                "123", // invalid
                null,
                new BigDecimal("9.4981"),
                new BigDecimal("76.3388"),
                new BigDecimal("20.00"),
                null
        );

        assertThrows(IllegalArgumentException.class, () -> deliveryService.updateShopLocation(invalidReq),
                "Updating shop location with invalid PIN code must throw IllegalArgumentException");

        // Static validator check
        assertFalse(DeliveryService.isValidIndianPinCode("123"));
        assertFalse(DeliveryService.isValidIndianPinCode("ABCDEF"));
        assertFalse(DeliveryService.isValidIndianPinCode("095003")); // Starts with 0
        assertFalse(DeliveryService.isValidIndianPinCode("6950031")); // 7 digits
        assertFalse(DeliveryService.isValidIndianPinCode(null));
        assertTrue(DeliveryService.isValidIndianPinCode("695003"));
        assertTrue(DeliveryService.isValidIndianPinCode("682001"));
    }

    @Test
    @DisplayName("Sales report does not invent city or PIN code when delivery address is unrecorded")
    void salesReport_doesNotInventCityOrPincode_whenDeliveryAddressMissing() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        Order orderWithoutAddress = new Order(
                "ORD-NOADDR-" + UUID.randomUUID().toString().substring(0, 6),
                testCustomer,
                "DELIVERED",
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500.00"),
                "COMPLETED",
                "ONLINE",
                "{}", // empty delivery address JSON (missing city and postalCode)
                new BigDecimal("1.50"),
                true,
                "Standard Slot"
        );
        orderWithoutAddress.setPaidAt(now);
        orderRepository.save(orderWithoutAddress);

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        SalesReportOrderDto orderDto = report.orders().stream()
                .filter(o -> o.orderNumber().equals(orderWithoutAddress.getOrderNumber()))
                .findFirst()
                .orElse(null);

        assertNotNull(orderDto);
        assertEquals("", orderDto.shippingCity(), "Missing customer delivery address must NOT invent 'Thiruvananthapuram'");
        assertEquals("", orderDto.shippingPincode(), "Missing customer delivery address must NOT invent '695003'");
    }

    @Test
    @DisplayName("Existing delivery calculations remain intact with verified shop location")
    void existingDeliveryCalculations_remainIntact() {
        shopLocationRepository.deleteAll();
        ShopLocation shop = new ShopLocation(
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
        shopLocationRepository.save(shop);

        // Destination at Pattom: 8.5244, 76.9442 (~1.1 km)
        DeliveryEligibilityResponse resp = deliveryService.checkDeliveryEligibility(8.5244, 76.9442, new BigDecimal("300.00"));
        assertTrue(resp.isEligible(), "Nearby location must remain eligible");
        assertTrue(resp.distanceKm().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(resp.distanceKm().compareTo(new BigDecimal("5.00")) < 0);
        // Under ₹500 threshold -> standard delivery charge applied
        assertEquals(new BigDecimal("50.00"), resp.deliveryCharge());
    }
}
