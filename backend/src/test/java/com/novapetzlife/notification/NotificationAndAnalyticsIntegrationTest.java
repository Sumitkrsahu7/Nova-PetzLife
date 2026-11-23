package com.novapetzlife.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.analytics.AnalyticsService;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.Cart;
import com.novapetzlife.cart.CartItem;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.AddressCreateRequest;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.support.dto.AddMessageRequest;
import com.novapetzlife.support.dto.CreateInquiryRequest;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.google.GoogleReviewRepository;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationAndAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private GoogleReviewRepository googleReviewRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PetProfileRepository petProfileRepository;

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
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    private Customer testCustomer;
    private Product testProduct;
    private Brand testBrand;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        googleReviewRepository.deleteAll();
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

        // 1. Ensure verified shop location at Kuravankonam
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

        // 2. Customer
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User user = userRepository.findByEmail("suresh.notif@example.com").orElseGet(() ->
                userRepository.save(new User("suresh.notif@example.com", "Password@123", "Suresh", "Gopi", "+9194471" + suffix))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(user);

        // 3. Category & Brand
        testCategory = categoryRepository.save(new Category("Dog Treats", "dog-treats-" + suffix, "Dog Treats Category", null, 1));
        testBrand = brandRepository.save(new Brand("Pedigree", "pedigree-" + suffix, "Pedigree Pet Care"));

        // 4. Product
        testProduct = new Product(
                testBrand,
                testCategory,
                "Pedigree Dentastix Large",
                "pedigree-dentastix-large-" + suffix,
                "PED-DENTA-" + suffix,
                new BigDecimal("600.00"),
                new BigDecimal("550.00")
        );
        ProductReturnPolicy policy = new ProductReturnPolicy();
        policy.setProduct(testProduct);
        policy.setReturnAllowed(true);
        policy.setReturnWindowDays(7);
        policy.setRefundAllowed(true);
        testProduct.setReturnPolicy(policy);
        testProduct.setIsActive(true);
        testProduct = productRepository.save(testProduct);
        inventoryService.initializeStock(testProduct, null, 15, 3);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        googleReviewRepository.deleteAll();
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "suresh.notif@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Notifications: Order checkout triggers automated email and WhatsApp order confirmation notifications")
    void testCheckoutTriggersNotification() throws Exception {
        String sessionToken = "session-notif-" + UUID.randomUUID();
        Cart cart = cartRepository.save(new Cart(testCustomer, sessionToken));
        cartItemRepository.save(new CartItem(cart, testProduct, null, 1, new BigDecimal("550.00")));

        AddressCreateRequest address = new AddressCreateRequest(
                "Suresh Gopi",
                testCustomer.getPhone(),
                "Kowdiar Main Avenue",
                null,
                null,
                "Thiruvananthapuram",
                "Kerala",
                "695003",
                new BigDecimal("8.5241"),
                new BigDecimal("76.9602"),
                true
        );

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(),
                sessionToken,
                testCustomer.getEmail(),
                testCustomer.getPhone(),
                "Suresh",
                "Gopi",
                null,
                address,
                8.5241,
                76.9602,
                "Morning (9:00 AM - 1:00 PM)",
                "COD",
                null,
                "Deliver to security desk"
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Verify notifications were logged in the database
        List<Notification> notifs = notificationRepository.findAll();
        assertFalse(notifs.isEmpty());

        boolean hasEmailConfirmation = notifs.stream()
                .anyMatch(n -> "EMAIL".equals(n.getChannel()) && "ORDER_CONFIRMATION".equals(n.getTemplate()));
        boolean hasWhatsAppConfirmation = notifs.stream()
                .anyMatch(n -> "WHATSAPP".equals(n.getChannel()) && "ORDER_CONFIRMATION_WA".equals(n.getTemplate()));

        assertTrue(hasEmailConfirmation, "Expected ORDER_CONFIRMATION email notification");
        assertTrue(hasWhatsAppConfirmation, "Expected ORDER_CONFIRMATION WhatsApp notification");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Notifications: Order status update triggers SMS notification to customer")
    void testOrderStatusUpdateNotification() throws Exception {
        Order order = new Order();
        order.setOrderNumber("ORD-DISPATCH-TEST");
        order.setCustomer(testCustomer);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PENDING");
        order.setPaymentMethod("COD");
        order.setTotalAmount(new BigDecimal("550.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setDeliveryCharge(BigDecimal.ZERO);
        order.setTaxAmount(new BigDecimal("83.90"));
        order.setFinalPayableAmount(new BigDecimal("550.00"));
        order.setShopDistanceKm(new BigDecimal("1.50"));
        order.setIsWithinRadius(true);
        order.setDeliverySlot("Evening");
        order.setDeliveryAddressJson("{}");
        order = orderRepository.save(order);

        // Update status to OUT_FOR_DELIVERY
        mockMvc.perform(patch("/api/v1/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("newStatus", "OUT_FOR_DELIVERY", "reason", "Rider assigned"))))
                .andExpect(status().isOk());

        List<Notification> outForDeliveryNotifs = notificationRepository.findAll().stream()
                .filter(n -> "ORDER_OUT_FOR_DELIVERY".equals(n.getTemplate()))
                .toList();

        assertFalse(outForDeliveryNotifs.isEmpty());
        assertEquals("SMS", outForDeliveryNotifs.get(0).getChannel());
        assertEquals(testCustomer.getPhone(), outForDeliveryNotifs.get(0).getRecipient());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Support: Customer can submit inquiry, view ticket, and exchange messages with store staff")
    void testSupportInquiryWorkflow() throws Exception {
        CreateInquiryRequest inquiry = new CreateInquiryRequest(
                "Suresh Gopi",
                "suresh.gopi@example.com",
                "+919447199887",
                null,
                "Royal Canin 15kg packaging question",
                "Is the 15kg Royal Canin adult bag currently in stock at Kuravankonam?",
                "MEDIUM"
        );

        // 1. Submit inquiry
        String responseContent = mockMvc.perform(post("/api/v1/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketNumber").value(startsWith("TKT-")))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.guestAccessSecret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String ticketNumber = objectMapper.readTree(responseContent).path("data").path("ticketNumber").asText();
        String guestSecret = objectMapper.readTree(responseContent).path("data").path("guestAccessSecret").asText();

        // 2. Fetch ticket by ticket number with guest access token header
        mockMvc.perform(get("/api/v1/support/tickets/" + ticketNumber)
                        .header("X-Guest-Access-Token", guestSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].message").value(containsString("15kg Royal Canin")));

        // 3. Customer appends a reply with guest access token header
        AddMessageRequest customerReply = new AddMessageRequest("Also wanted to know if free home delivery applies to this size.", "CUSTOMER", "Suresh Gopi", null);
        mockMvc.perform(post("/api/v1/support/tickets/" + ticketNumber + "/messages")
                        .header("X-Guest-Access-Token", guestSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerReply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", hasSize(2)));

        // 4. Staff replies (Admin endpoint)
        AddMessageRequest staffReply = new AddMessageRequest("Yes, 15kg is in stock at Kuravankonam and orders above ₹500 get free delivery!", "STAFF", "Kuravankonam Desk", null);
        mockMvc.perform(post("/api/v1/admin/support/tickets/" + ticketNumber + "/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staffReply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", hasSize(3)))
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_CUSTOMER"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Analytics: Correctly calculates total revenue, average order value, and order distribution")
    void testAnalyticsSummaryCalculation() throws Exception {
        // Setup Order 1: DELIVERED (₹1200.00)
        Order o1 = new Order(
                "ORD-ANALYTICS-1",
                testCustomer,
                "DELIVERED",
                new BigDecimal("1200.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1200.00"),
                "COMPLETED",
                "RAZORPAY",
                "{}",
                new BigDecimal("2.50"),
                true,
                "Morning"
        );
        o1 = orderRepository.save(o1);

        // Setup Order 2: CONFIRMED (₹450.00)
        Order o2 = new Order(
                "ORD-ANALYTICS-2",
                testCustomer,
                "CONFIRMED",
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("450.00"),
                "PENDING",
                "COD",
                "{}",
                new BigDecimal("1.80"),
                true,
                "Evening"
        );
        o2 = orderRepository.save(o2);

        // Setup Order 3: CANCELLED (₹300.00) - Should NOT count towards revenue
        Order o3 = new Order(
                "ORD-ANALYTICS-3",
                testCustomer,
                "CANCELLED",
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                "CANCELLED",
                "COD",
                "{}",
                new BigDecimal("3.20"),
                true,
                "Morning"
        );
        o3 = orderRepository.save(o3);

        // Setup Refund: ₹200.00
        refundRepository.save(new Refund(o1, null, "rfnd_test_01", new BigDecimal("200.00"), "SUCCESS", "Return refund", "STAFF"));

        // Query analytics
        mockMvc.perform(get("/api/v1/admin/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalOrders").value(3))
                .andExpect(jsonPath("$.data.deliveredOrders").value(1))
                .andExpect(jsonPath("$.data.cancelledOrders").value(1))
                // Total revenue = 1200.00 + 450.00 = 1650.00
                .andExpect(jsonPath("$.data.totalRevenue").value(1650.00))
                // AOV = 1650.00 / 2 valid orders = 825.00
                .andExpect(jsonPath("$.data.averageOrderValue").value(825.00))
                .andExpect(jsonPath("$.data.totalRefunded").value(200.00));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Analytics: Identifies products with stock below threshold for Kuravankonam reordering")
    void testLowStockAlertReporting() throws Exception {
        // Product with 2 items in stock (threshold is 5)
        Product lowProduct = new Product(
                testBrand,
                testCategory,
                "Catnip Toy Mouse",
                "catnip-toy-mouse-" + UUID.randomUUID().toString().substring(0, 6),
                "CAT-MOUSE-001",
                new BigDecimal("199.00"),
                new BigDecimal("179.00")
        );
        lowProduct.setIsActive(true);
        lowProduct = productRepository.save(lowProduct);
        inventoryService.initializeStock(lowProduct, null, 2, 5);

        mockMvc.perform(get("/api/v1/admin/analytics/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.sku == 'CAT-MOUSE-001')].currentStock").value(hasItem(2)))
                .andExpect(jsonPath("$.data[?(@.sku == 'CAT-MOUSE-001')].lowStockThreshold").value(hasItem(5)));
    }
}
