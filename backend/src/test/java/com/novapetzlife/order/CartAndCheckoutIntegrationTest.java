package com.novapetzlife.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.AddToCartRequest;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.AddressCreateRequest;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerAddress;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartAndCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private PetProfileRepository petProfileRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private ReturnInspectionRepository returnInspectionRepository;

    @Autowired
    private ReturnRequestItemRepository returnRequestItemRepository;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        notificationRepository.deleteAll();
        couponUsageRepository.deleteAll();
        couponRepository.deleteAll();
        petProfileRepository.deleteAll();
        returnInspectionRepository.deleteAll();
        returnRequestItemRepository.deleteAll();
        returnRequestRepository.deleteAll();
        refundRepository.deleteAll();
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        customerAddressRepository.deleteAll();
        customerRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();

        // Ensure Shop location is verified
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

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Brand brand = brandRepository.save(new Brand("Arden Grange", "arden-grange-" + suffix, "Hypoallergenic pet nutrition"));
        Category category = categoryRepository.save(new Category("Dog Food", "dog-food-" + suffix, "Nutritious dog food", null, 1));

        testProduct = new Product(
                brand,
                category,
                "Arden Grange Adult Dog Fresh Chicken & Rice",
                "arden-grange-chicken-" + suffix,
                "AG-DOG-" + suffix.toUpperCase(),
                new BigDecimal("1250.00"),
                new BigDecimal("1150.00")
        );
        testProduct.setPetType("Dog");
        testProduct.setLifeStage("Adult");

        ProductReturnPolicy returnPolicy = new ProductReturnPolicy();
        returnPolicy.setProduct(testProduct);
        returnPolicy.setReturnAllowed(true);
        returnPolicy.setReturnWindowDays(10);
        returnPolicy.setRefundAllowed(true);
        returnPolicy.setOriginalPackagingRequired(true);
        returnPolicy.setUnusedConditionRequired(true);
        returnPolicy.setCustomConditions("Must have intact security seal");
        testProduct.setReturnPolicy(returnPolicy);

        testProduct = productRepository.save(testProduct);
        inventoryService.initializeStock(testProduct, null, 15, 3);

        userRepository.findByEmail("customer@novapetzlife.com").orElseGet(() -> {
            User u = new User("customer@novapetzlife.com", "Password@123", "Customer", "User", "+919447123456");
            return userRepository.save(u);
        });

        userRepository.findByEmail("admin@novapetzlife.com").orElseGet(() -> {
            User u = new User("admin@novapetzlife.com", "Password@123", "Admin", "User", "+919847012345");
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("Shopping Cart: Guest adds item to cart with session token and validates stock availability")
    void testGuestCartOperations() throws Exception {
        String sessionToken = "session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 2, sessionToken);

        // 1. Add item
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(2300.00)) // 2 * ₹1150.00
                .andExpect(jsonPath("$.data.items[0].productName", containsString("Arden Grange")));

        // 2. Retrieve active cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("X-Session-Token", sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(15));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Order Checkout: Seamless checkout, 20 km Kuravankonam distance check, stock reservation, and return snapshot")
    void testCompleteCheckoutFlow() throws Exception {
        String sessionToken = "checkout_session_" + UUID.randomUUID();

        // 1. Add to cart
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        // 2. Perform Checkout (Pattom location ~1.1 km away from Kuravankonam store)
        AddressCreateRequest address = new AddressCreateRequest(
                "Suresh Kumar",
                "+919447123456",
                "TC 4/1202, Near Pattom Palace",
                "Pattom P.O.",
                "Opposite LIC Office",
                "Thiruvananthapuram",
                "Kerala",
                "695004",
                new BigDecimal("8.524400"),
                new BigDecimal("76.944200"),
                true
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null,
                sessionToken,
                "suresh.kumar@example.com",
                "+919447123456",
                "Suresh",
                "Kumar",
                null,
                address,
                8.5244,
                76.9442,
                "Evening Slot (05:00 PM - 08:30 PM)",
                "COD",
                "Please ring bell at gate"
        );

        String responseJson = mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.isWithinRadius").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].returnPolicySnapshot", containsString("Must have intact security seal")))
                .andExpect(jsonPath("$.data.items[0].returnPolicySnapshot", containsString("\"returnWindowDays\":10")))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = objectMapper.readTree(responseJson).path("data").path("orderNumber").asText();

        // 3. Verify stock reservation: Available stock should be 15 - 1 = 14
        assertEquals(14, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 4. Verify public order tracking
        mockMvc.perform(get("/api/v1/orders/" + orderNumber + "/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.timeline", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Security: Unauthenticated checkout is rejected with 401 or 403")
    void testUnauthenticatedCheckoutRejected() throws Exception {
        String sessionToken = "unauth_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null, null, null,
                8.5244, 76.9442, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Validation: Authenticated checkout without valid delivery address is rejected with 400 Bad Request")
    void testCheckoutWithoutAddressRejected() throws Exception {
        String sessionToken = "no_addr_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null, null, null,
                8.5244, 76.9442, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("delivery address is mandatory")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 1: Valid saved address within delivery radius succeeds")
    void testCheckoutWithValidSavedAddress() throws Exception {
        String sessionToken = "saved_addr_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("customer@novapetzlife.com").orElseThrow();
        Customer customer = customerService.getOrCreateCustomerForUser(user);

        CustomerAddress savedAddress = new CustomerAddress(
                customer,
                "Customer Saved",
                "+919447123456",
                "TC 12/345 Saved Villa",
                "Kowdiar",
                "Near Kowdiar Palace",
                "Thiruvananthapuram",
                "Kerala",
                "695003",
                new BigDecimal("8.524100"),
                new BigDecimal("76.960200"),
                true
        );
        savedAddress = customerAddressRepository.save(savedAddress);

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                savedAddress.getId(), null, 8.5241, 76.9602, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.isWithinRadius").value(true));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 2: Attempting to use another customer's saved address is rejected")
    void testCheckoutWithAnotherCustomerSavedAddressRejected() throws Exception {
        String sessionToken = "other_cust_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        User otherUser = userRepository.save(new User("victim@novapetzlife.com", "Password@123", "Victim", "User", "+919447123999"));
        Customer otherCustomer = customerService.getOrCreateCustomerForUser(otherUser);

        CustomerAddress otherAddress = new CustomerAddress(
                otherCustomer,
                "Victim Customer",
                "+919447123999",
                "House 99, Secret Colony",
                "Vellayambalam",
                "Museum",
                "Thiruvananthapuram",
                "Kerala",
                "695033",
                new BigDecimal("8.515000"),
                new BigDecimal("76.955000"),
                true
        );
        otherAddress = customerAddressRepository.save(otherAddress);

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                otherAddress.getId(), null, 8.5150, 76.9550, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Selected delivery address does not exist or does not belong to your account")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 3: Browser-supplied coordinates cannot override saved address coordinates (out-of-radius saved address rejected despite in-radius browser coordinates)")
    void testBrowserCoordinatesCannotOverrideSavedAddress() throws Exception {
        String sessionToken = "override_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("customer@novapetzlife.com").orElseThrow();
        Customer customer = customerService.getOrCreateCustomerForUser(user);

        // Saved address in Kollam (~60 km away, out of 20 km radius)
        CustomerAddress distantAddress = new CustomerAddress(
                customer,
                "Distant Residence",
                "+919447123456",
                "Beach Road",
                "Chinnakada",
                "Clock Tower",
                "Kollam",
                "Kerala",
                "691001",
                new BigDecimal("8.893200"),
                new BigDecimal("76.614100"),
                true
        );
        distantAddress = customerAddressRepository.save(distantAddress);

        // Browser attempts to override with in-radius Kuravankonam coordinates (8.5284, 76.9532)
        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                distantAddress.getId(), null, 8.5284, 76.9532, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("unavailable at this location")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 4: Saved address coordinates are used for radius validation (in-radius saved address succeeds despite out-of-radius browser coordinates)")
    void testSavedAddressCoordinatesUsedForRadius() throws Exception {
        String sessionToken = "stored_coords_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("customer@novapetzlife.com").orElseThrow();
        Customer customer = customerService.getOrCreateCustomerForUser(user);

        // Saved address in Kowdiar (~1.2 km away, in-radius)
        CustomerAddress kowdiarAddress = new CustomerAddress(
                customer,
                "Kowdiar Home",
                "+919447123456",
                "Kowdiar Gardens",
                "Kowdiar",
                "Palace Road",
                "Thiruvananthapuram",
                "Kerala",
                "695003",
                new BigDecimal("8.524100"),
                new BigDecimal("76.960200"),
                true
        );
        kowdiarAddress = customerAddressRepository.save(kowdiarAddress);

        // Browser passes bogus out-of-radius coordinates (lat 8.8932, lon 76.6141)
        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                kowdiarAddress.getId(), null, 8.8932, 76.6141, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isWithinRadius").value(true));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 5: Saved address with null or zero coordinates handled safely (geocoded or rejected without using browser coordinates)")
    void testMissingSavedAddressCoordinatesHandledSafely() throws Exception {
        String sessionToken = "missing_coords_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("customer@novapetzlife.com").orElseThrow();
        Customer customer = customerService.getOrCreateCustomerForUser(user);

        // Saved address with null coordinates
        CustomerAddress nullCoordsAddress = new CustomerAddress(
                customer,
                "Ungeocoded Flat",
                "+919447123456",
                "Random Flat 404",
                "Unknown Locality",
                "Near Tree",
                "Thiruvananthapuram",
                "Kerala",
                "695001",
                null,
                null,
                false
        );
        nullCoordsAddress = customerAddressRepository.save(nullCoordsAddress);

        // Browser attempts to supply arbitrary coordinates to bypass geocoding
        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                nullCoordsAddress.getId(), null, 8.5284, 76.9532, "Evening Slot", "COD", null
        );

        // Server does NOT trust browser coordinates; since offline geocoding returns null, fails with validation error
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("lacks valid geographic coordinates and could not be geocoded")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 6: Inline address with out-of-area postal code cannot bypass delivery validation using manipulated client coordinates")
    void testInlineAddressCoordinatesCannotBypassValidation() throws Exception {
        String sessionToken = "bypass_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        // Inline address with Kochi PIN 682001 (out of area), but browser provides Kuravankonam coordinates (8.5284, 76.9532)
        AddressCreateRequest address = new AddressCreateRequest(
                "Kochi Resident",
                "+919847123456",
                "MG Road",
                "Ernakulam",
                "Near Metro Station",
                "Kochi",
                "Kerala",
                "682001",
                new BigDecimal("8.528448"),
                new BigDecimal("76.953268"),
                false
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                null, address, 8.5284, 76.9532, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Delivery is currently only available within 20 km of our Kuravankonam store")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 7: Address outside the 20 km radius is rejected")
    void testAddressOutsideDeliveryRadiusRejected() throws Exception {
        String sessionToken = "outside_radius_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        // Address in Neyyattinkara / South Trivandrum border > 25 km away
        AddressCreateRequest address = new AddressCreateRequest(
                "South TVM Resident",
                "+919847123456",
                "Main Road",
                "Amaravila",
                "Checkpost",
                "Neyyattinkara",
                "Kerala",
                "695122",
                new BigDecimal("8.390000"),
                new BigDecimal("77.080000"),
                false
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                null, address, 8.390000, 77.080000, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("unavailable at this location")));
    }

    @Test
    @WithMockUser(username = "customer@novapetzlife.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Delivery Address Test 8: Address inside the 20 km radius is accepted")
    void testAddressInsideDeliveryRadiusAccepted() throws Exception {
        String sessionToken = "inside_radius_session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        // Pattom address (~1.1 km away)
        AddressCreateRequest address = new AddressCreateRequest(
                "Pattom Resident",
                "+919847123456",
                "TC 4/1202",
                "Pattom",
                "Near Palace",
                "Thiruvananthapuram",
                "Kerala",
                "695004",
                new BigDecimal("8.524400"),
                new BigDecimal("76.944200"),
                false
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null, sessionToken, null, null, null, null,
                null, address, 8.5244, 76.9442, "Evening Slot", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.isWithinRadius").value(true));
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Admin Order Management: Advance lifecycle and release stock upon order cancellation")
    void testAdminOrderStatusAndStockRelease() throws Exception {
        String sessionToken = "admin_cancel_session_" + UUID.randomUUID();

        // 1. Add item to cart and checkout
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 3, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        AddressCreateRequest adminOrderAddress = new AddressCreateRequest(
                "Rahul Menon",
                "+919847012345",
                "TC 5/1230 Kowdiar",
                "Kowdiar P.O.",
                "Near Tennis Club",
                "Thiruvananthapuram",
                "Kerala",
                "695003",
                new BigDecimal("8.524400"),
                new BigDecimal("76.944200"),
                true
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null,
                sessionToken,
                "rahul@example.com",
                "+919847012345",
                "Rahul",
                "Menon",
                null,
                adminOrderAddress,
                8.5244,
                76.9442,
                "Morning Slot (09:00 AM - 01:00 PM)",
                "COD",
                null
        );

        String responseJson = mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseJson).path("data").path("id").asLong();

        // Available stock reserved: 15 - 3 = 12
        assertEquals(12, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 2. Admin advances to PACKED
        OrderStatusUpdateRequest processingReq = new OrderStatusUpdateRequest("PACKED", "Order packed at fulfillment station");
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(processingReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PACKED"));

        // 3. Admin cancels order -> Reserved stock should be released back!
        OrderStatusUpdateRequest cancelReq = new OrderStatusUpdateRequest("CANCELLED", "Customer requested cancellation");
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));

        // Available stock restored back to 15!
        assertEquals(15, inventoryService.getAvailableStock(testProduct.getId(), null));
    }
}
