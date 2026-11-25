package com.novapetzlife.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
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
import com.novapetzlife.payment.dto.CreatePaymentOrderRequest;
import com.novapetzlife.payment.dto.VerifyPaymentRequest;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.tax.InvoiceService;
import com.novapetzlife.tax.InvoiceDto;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAndInvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private RazorpaySignatureVerifier signatureVerifier;

    @Autowired
    private RazorpayConfig razorpayConfig;

    @org.springframework.boot.test.mock.mockito.MockBean
    private RazorpayClientService razorpayClientService;

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
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InvoiceService invoiceService;

    private Order testOrder;
    private Product testProduct;
    private Customer testCustomer;

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
        User user = userRepository.findByEmail("aditya.payment@example.com").orElseGet(() ->
                userRepository.save(new User("aditya.payment@example.com", "Password@123", "Aditya", "Menon", "+919876543210"))
        );
        Customer customer = customerService.getOrCreateCustomerForUser(user);
        this.testCustomer = customer;

        // 3. Catalog & Return Policy
        Brand brand = brandRepository.save(new Brand("Royal Canin", "royal-canin-" + suffix, "Specialized pet nutrition"));
        Category category = categoryRepository.save(new Category("Cat Food", "cat-food-" + suffix, "Nutritious cat food", null, 1));

        testProduct = new Product(
                brand,
                category,
                "Royal Canin Adult Cat Food 2kg",
                "royal-canin-cat-" + suffix,
                "RC-CAT-2KG-" + suffix.toUpperCase(),
                new BigDecimal("1200.00"),
                new BigDecimal("1180.00")
        );

        ProductReturnPolicy policy = new ProductReturnPolicy();
        policy.setProduct(testProduct);
        policy.setReturnAllowed(true);
        policy.setReturnWindowDays(7);
        policy.setRefundAllowed(true);
        testProduct.setReturnPolicy(policy);
        testProduct = productRepository.save(testProduct);

        // 4. Test Order
        testOrder = new Order();
        testOrder.setOrderNumber("ORD-" + suffix.toUpperCase());
        testOrder.setCustomer(customer);
        testOrder.setOrderStatus("PENDING_PAYMENT");
        testOrder.setPaymentStatus("PENDING");
        testOrder.setPaymentMethod("RAZORPAY");
        testOrder.setTotalAmount(new BigDecimal("1180.00"));
        testOrder.setDiscountAmount(BigDecimal.ZERO);
        testOrder.setDeliveryCharge(BigDecimal.ZERO);
        testOrder.setTaxAmount(new BigDecimal("180.00"));
        testOrder.setFinalPayableAmount(new BigDecimal("1180.00"));
        testOrder.setShopDistanceKm(new BigDecimal("1.20"));
        testOrder.setIsWithinRadius(true);
        testOrder.setDeliverySlot("Evening (4:00 PM - 8:00 PM)");
        testOrder.setDeliveryAddressJson("{\"recipientName\":\"Aditya Menon\",\"phone\":\"+919876543210\",\"addressLine1\":\"Pattom\",\"city\":\"Thiruvananthapuram\",\"state\":\"Kerala\",\"postalCode\":\"695004\"}");

        String returnSnapshot = "{\"returnAllowed\":true,\"returnWindowDays\":7,\"conditions\":\"Sealed packaging required\"}";
        String refundSnapshot = "{\"refundAllowed\":true,\"refundPercentage\":100}";

        OrderItem orderItem = new OrderItem(
                testOrder,
                testProduct,
                null,
                testProduct.getName(),
                brand.getName(),
                testProduct.getSku(),
                1,
                new BigDecimal("1180.00"),
                new BigDecimal("1180.00"),
                new BigDecimal("18.00"),
                new BigDecimal("180.00"),
                "2309",
                returnSnapshot,
                refundSnapshot
        );
        testOrder.addItem(orderItem);
        testOrder = orderRepository.save(testOrder);

        org.mockito.Mockito.when(razorpayClientService.isConfigured()).thenReturn(true);
        org.mockito.Mockito.when(razorpayClientService.createOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new RazorpayClientService.GatewayOrderResult("order_mock_gateway_12345", 118000L, "INR"));

        org.mockito.Mockito.when(razorpayClientService.createRefund(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_gateway_12345", "processed", 118000L));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Should create Razorpay payment order with paise amount and key ID")
    void testCreatePaymentOrder_Success() throws Exception {
        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", is(testOrder.getOrderNumber())))
                .andExpect(jsonPath("$.data.amountInPaise", is(118000))) // 1180.00 * 100 paise
                .andExpect(jsonPath("$.data.currency", is("INR")))
                .andExpect(jsonPath("$.data.keyId", is(razorpayConfig.getKeyId())))
                .andExpect(jsonPath("$.data.razorpayOrderId", startsWith("order_")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Should reject payment order creation if order final payable amount is zero or negative")
    void testCreatePaymentOrder_ZeroOrNegativeAmount_Rejected() throws Exception {
        // Zero amount test
        testOrder.setFinalPayableAmount(BigDecimal.ZERO);
        orderRepository.save(testOrder);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Order final payable amount must be positive")));

        // Negative amount test
        testOrder.setFinalPayableAmount(new BigDecimal("-50.00"));
        orderRepository.save(testOrder);

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Order final payable amount must be positive")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Should calculate exact paise for valid two-decimal amounts (100.00, 100.50, 100.01)")
    void testCreatePaymentOrder_ExactTwoDecimalAmounts() throws Exception {
        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());

        // Test 100.00 -> 10000 paise
        testOrder.setFinalPayableAmount(new BigDecimal("100.00"));
        orderRepository.save(testOrder);
        org.mockito.Mockito.when(razorpayClientService.createOrder(
                org.mockito.ArgumentMatchers.eq(testOrder.getOrderNumber()),
                org.mockito.ArgumentMatchers.eq(10000L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new RazorpayClientService.GatewayOrderResult("order_100_00", 10000L, "INR"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInPaise", is(10000)));

        // Test 100.50 -> 10050 paise
        testOrder.setFinalPayableAmount(new BigDecimal("100.50"));
        orderRepository.save(testOrder);
        org.mockito.Mockito.when(razorpayClientService.createOrder(
                org.mockito.ArgumentMatchers.eq(testOrder.getOrderNumber()),
                org.mockito.ArgumentMatchers.eq(10050L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new RazorpayClientService.GatewayOrderResult("order_100_50", 10050L, "INR"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInPaise", is(10050)));

        // Test 100.01 -> 10001 paise
        testOrder.setFinalPayableAmount(new BigDecimal("100.01"));
        orderRepository.save(testOrder);
        org.mockito.Mockito.when(razorpayClientService.createOrder(
                org.mockito.ArgumentMatchers.eq(testOrder.getOrderNumber()),
                org.mockito.ArgumentMatchers.eq(10001L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new RazorpayClientService.GatewayOrderResult("order_100_01", 10001L, "INR"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInPaise", is(10001)));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Should reuse matching gateway order and supersede mismatched amount payment records")
    void testCreatePaymentOrder_IdempotencyAndMismatchedSupersede() throws Exception {
        // Case A: Pre-existing CREATED payment with matching amount should be reused
        Payment matchingPayment = new Payment(testOrder, "order_matching_123", testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(matchingPayment);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());
        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.razorpayOrderId", is("order_matching_123")));

        // Case B: If order amount changes (e.g. delivery fee or coupon update), the old payment is superseded
        testOrder.setFinalPayableAmount(new BigDecimal("1500.00"));
        orderRepository.save(testOrder);

        org.mockito.Mockito.when(razorpayClientService.createOrder(
                org.mockito.ArgumentMatchers.eq(testOrder.getOrderNumber()),
                org.mockito.ArgumentMatchers.eq(150000L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new RazorpayClientService.GatewayOrderResult("order_fresh_456", 150000L, "INR"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.razorpayOrderId", is("order_fresh_456")));

        Payment oldPayment = paymentRepository.findByGatewayOrderId("order_matching_123").orElseThrow();
        assertEquals("EXPIRED", oldPayment.getStatus());
    }

    @Test
    @DisplayName("Payment Test 1: Unauthenticated request to verify endpoint is rejected with 401")
    void testUnauthenticatedPaymentVerifyRejected() throws Exception {
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                "order_unauth_123",
                "pay_unauth_456",
                "signature_abc"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 2: Authenticated customer successfully verifies their own payment")
    void testCustomerCanVerifyOwnPayment() throws Exception {
        String razorpayOrderId = "order_mock_test_123";
        String razorpayPaymentId = "pay_mock_test_456";
        String secret = razorpayConfig.getKeySecret();
        String validSignature = signatureVerifier.calculateHmacSha256(razorpayOrderId + "|" + razorpayPaymentId, secret);

        // Pre-create payment record
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                razorpayOrderId,
                razorpayPaymentId,
                validSignature
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", is(testOrder.getOrderNumber())))
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.transactionId", is(razorpayPaymentId)));

        // Verify order in database
        Order updatedOrder = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("CONFIRMED", updatedOrder.getOrderStatus());
        assertEquals("COMPLETED", updatedOrder.getPaymentStatus());

        // Verify payment in database
        Payment updatedPayment = paymentRepository.findByGatewayOrderId(razorpayOrderId).orElseThrow();
        assertEquals("SUCCESS", updatedPayment.getStatus());
        assertEquals(razorpayPaymentId, updatedPayment.getGatewayPaymentId());

        // Verify invoice was automatically generated
        assertTrue(invoiceRepository.findByOrderId(testOrder.getId()).isPresent());
    }

    @Test
    @WithMockUser(username = "attacker@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 3: Authenticated customer attempting to verify another customer's payment is rejected with 403 Forbidden")
    void testCustomerCannotVerifyAnotherCustomerPayment() throws Exception {
        // Register attacker user
        userRepository.findByEmail("attacker@example.com").orElseGet(() ->
                userRepository.save(new User("attacker@example.com", "Password@123", "Attacker", "User", "+919876543299"))
        );

        String razorpayOrderId = "order_attacker_test_123";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                razorpayOrderId,
                "pay_attacker_456",
                "simulated_rzp_signature"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 4: Gateway order ID belonging to a different application order is rejected")
    void testGatewayOrderIdBelongingToDifferentOrderRejected() throws Exception {
        // Create a second order for the customer
        Order secondOrder = new Order();
        secondOrder.setOrderNumber("ORD-SECOND-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        secondOrder.setCustomer(testOrder.getCustomer());
        secondOrder.setOrderStatus("PENDING_PAYMENT");
        secondOrder.setPaymentStatus("PENDING");
        secondOrder.setPaymentMethod("RAZORPAY");
        secondOrder.setTotalAmount(new BigDecimal("500.00"));
        secondOrder.setDiscountAmount(BigDecimal.ZERO);
        secondOrder.setDeliveryCharge(BigDecimal.ZERO);
        secondOrder.setTaxAmount(BigDecimal.ZERO);
        secondOrder.setFinalPayableAmount(new BigDecimal("500.00"));
        secondOrder.setShopDistanceKm(new BigDecimal("2.50"));
        secondOrder.setIsWithinRadius(true);
        secondOrder.setDeliverySlot("Evening");
        secondOrder.setDeliveryAddressJson("{}");
        secondOrder = orderRepository.save(secondOrder);

        String orderTwoGatewayId = "order_gateway_for_second_order";
        Payment paymentForOrderTwo = new Payment(secondOrder, orderTwoGatewayId, secondOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(paymentForOrderTwo);

        // Try to verify testOrder using secondOrder's gateway ID
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                orderTwoGatewayId,
                "pay_mismatch_123",
                "simulated_rzp_signature"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Gateway order ID does not belong to the specified application order")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 5: Verification with an unknown/non-existent gateway order ID is rejected")
    void testUnknownGatewayOrderIdRejected() throws Exception {
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                "order_unknown_nonexistent_999",
                "pay_unknown_888",
                "simulated_rzp_signature"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No payment record found matching gateway order ID")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 6: Verification attempt does not create an untrusted payment record when gateway order ID is not found")
    void testVerifyDoesNotCreateUntrustedPaymentRecord() throws Exception {
        long countBefore = paymentRepository.count();
        String unknownGatewayId = "order_untrusted_never_persisted_777";

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                unknownGatewayId,
                "pay_untrusted_888",
                "simulated_rzp_signature"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        long countAfter = paymentRepository.count();
        assertEquals(countBefore, countAfter);
        assertTrue(paymentRepository.findByGatewayOrderId(unknownGatewayId).isEmpty());
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 7: Invalid signature fails verification and does not confirm or complete the order")
    void testInvalidSignatureDoesNotConfirmOrder() throws Exception {
        String razorpayOrderId = "order_mock_fake_999";
        String razorpayPaymentId = "pay_mock_fake_888";
        String bogusSignature = "bad_signature_attempt_12345";

        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                razorpayOrderId,
                razorpayPaymentId,
                bogusSignature
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Order remains unconfirmed
        Order unchangedOrder = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("PENDING_PAYMENT", unchangedOrder.getOrderStatus());
        assertEquals("PENDING", unchangedOrder.getPaymentStatus());

        // Payment marked as FAILED
        Payment updatedPayment = paymentRepository.findByGatewayOrderId(razorpayOrderId).orElseThrow();
        assertEquals("FAILED", updatedPayment.getStatus());
    }

    @Test
    @WithMockUser(username = "intruder@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Payment Test 8: Customer cannot view payment status for another customer's order (403 Forbidden)")
    void testPaymentStatusUnrelatedCustomerRejected() throws Exception {
        userRepository.findByEmail("intruder@example.com").orElseGet(() ->
                userRepository.save(new User("intruder@example.com", "Password@123", "Intruder", "User", "+919876543288"))
        );

        mockMvc.perform(get("/api/v1/payments/order/" + testOrder.getOrderNumber()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Payment Test 9: Admin roles can view payment status for any order")
    void testPaymentStatusAdminAllowed() throws Exception {
        userRepository.findByEmail("admin@novapetzlife.com").orElseGet(() ->
                userRepository.save(new User("admin@novapetzlife.com", "Password@123", "Admin", "User", "+919876543200"))
        );

        mockMvc.perform(get("/api/v1/payments/order/" + testOrder.getOrderNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", is(testOrder.getOrderNumber())));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Should reject simulated dev signature without exception (no bypass allowed)")
    void testVerifyPayment_SimulatedDevSignature_Rejected() throws Exception {
        String razorpayOrderId = "order_sim_111";
        String razorpayPaymentId = "pay_sim_222";

        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(),
                razorpayOrderId,
                razorpayPaymentId,
                "simulated_rzp_signature"
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid transaction signature")));
    }

    @Test
    @DisplayName("Should retrieve order invoice / provisional receipt with Kerala State Code 32 and amount in words")
    void testGetOrderGstInvoice_Success() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + testOrder.getOrderNumber() + "/invoice")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("aditya.payment@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.storeName", is("NOVA PETZLIFE")))
                .andExpect(jsonPath("$.data.storeStateCode", is("32")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.amountInWords", containsString("Rupees")));
    }

    @Test
    @DisplayName("Payment Test 10: Should process Razorpay webhook payment.captured with valid signature")
    void testRazorpayWebhook_PaymentCaptured_Success() throws Exception {
        String razorpayOrderId = "order_webhook_777";
        String razorpayPaymentId = "pay_webhook_888";

        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""
                + razorpayPaymentId + "\",\"order_id\":\"" + razorpayOrderId + "\"}}}}";

        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        Order updated = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("CONFIRMED", updated.getOrderStatus());
        assertEquals("COMPLETED", updated.getPaymentStatus());
    }

    @Test
    @DisplayName("Payment Test 11: Webhook rejected with HTTP 400 when webhook secret is empty string")
    void testRazorpayWebhook_EmptySecret_Rejected() throws Exception {
        String originalSecret = razorpayConfig.getWebhookSecret();
        try {
            razorpayConfig.setWebhookSecret("");
            String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test_empty\",\"order_id\":\"order_test_empty\"}}}}";

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-Razorpay-Signature", "any_incoming_signature_value")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Invalid Razorpay webhook signature")));
        } finally {
            razorpayConfig.setWebhookSecret(originalSecret);
        }
    }

    @Test
    @DisplayName("Payment Test 12: Webhook rejected with HTTP 400 when webhook secret is whitespace only")
    void testRazorpayWebhook_WhitespaceSecret_Rejected() throws Exception {
        String originalSecret = razorpayConfig.getWebhookSecret();
        try {
            razorpayConfig.setWebhookSecret("   \t   ");
            String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test_ws\",\"order_id\":\"order_test_ws\"}}}}";

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-Razorpay-Signature", "any_incoming_signature_value")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Invalid Razorpay webhook signature")));
        } finally {
            razorpayConfig.setWebhookSecret(originalSecret);
        }
    }

    @Test
    @DisplayName("Payment Test 13: Webhook rejected with HTTP 400 when webhook secret is null")
    void testRazorpayWebhook_NullSecret_Rejected() throws Exception {
        String originalSecret = razorpayConfig.getWebhookSecret();
        try {
            razorpayConfig.setWebhookSecret(null);
            String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test_null\",\"order_id\":\"order_test_null\"}}}}";

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-Razorpay-Signature", "any_incoming_signature_value")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Invalid Razorpay webhook signature")));
        } finally {
            razorpayConfig.setWebhookSecret(originalSecret);
        }
    }

    @Test
    @DisplayName("NPZ-VERIFY-001: All-or-nothing stock reservation rollback when second item is out of stock")
    void testAtomicMultiItemReservation_RollbackOnPartialStockout() throws Exception {
        // Product 1 has stock (5 units), Product 2 has 0 stock
        inventoryService.initializeStock(testProduct, null, 5, 2);
        int initialStock1 = inventoryService.getAvailableStock(testProduct.getId(), null);
        Product product2 = productRepository.save(new Product(
                testProduct.getBrand(), testProduct.getCategory(), "Out of Stock Toy", "toy-out", "SKU-OUT",
                new BigDecimal("500.00"), new BigDecimal("450.00")
        ));
        inventoryService.initializeStock(product2, null, 0, 2); // 0 available

        // Create cancelled order with both items
        Order order = new Order();
        order.setOrderNumber("ORD-ATOMIC-" + UUID.randomUUID().toString().substring(0, 8));
        order.setCustomer(testCustomer);
        order.setOrderStatus("CANCELLED");
        order.setPaymentStatus("PENDING_PAYMENT");
        order.setPaymentMethod("RAZORPAY");
        order.setTotalAmount(new BigDecimal("950.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setDeliveryCharge(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setFinalPayableAmount(new BigDecimal("950.00"));
        order.setShopDistanceKm(new BigDecimal("1.20"));
        order.setIsWithinRadius(true);
        order.setDeliveryAddressJson("{}");
        order = orderRepository.save(order);

        OrderItem item1 = new OrderItem(order, testProduct, null, testProduct.getName(), "Brand", "SKU1", 2, new BigDecimal("250.00"), new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, "9503", "{}", "{}");
        OrderItem item2 = new OrderItem(order, product2, null, product2.getName(), "Brand", "SKU2", 1, new BigDecimal("450.00"), new BigDecimal("450.00"), BigDecimal.ZERO, BigDecimal.ZERO, "9503", "{}", "{}");
        order.getItems().add(item1);
        order.getItems().add(item2);
        orderItemRepository.save(item1);
        orderItemRepository.save(item2);
        order = orderRepository.save(order);

        String razorpayOrderId = "order_atomic_fail";
        Payment payment = new Payment(order, razorpayOrderId, order.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_atomic_fail\",\"order_id\":\"" + razorpayOrderId + "\"}}}}";
        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Inventory check: Product 1 reservation MUST NOT leak! Available stock must remain equal to initial
        int currentStock1 = inventoryService.getAvailableStock(testProduct.getId(), null);
        assertEquals(initialStock1, currentStock1, "Product 1 stock must not be reserved when item 2 fails");

        // Order state check: marked PAYMENT_RECEIVED_OUT_OF_STOCK and NOT confirmed
        Order updated = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertEquals("CANCELLED", updated.getOrderStatus());
        assertEquals("PAYMENT_RECEIVED_OUT_OF_STOCK", updated.getPaymentStatus());
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("NPZ-VERIFY-002: verifyPayment handles cancelled order with available stock by re-reserving and confirming")
    void testVerifyPayment_CancelledOrder_StockAvailable_Succeeds() throws Exception {
        inventoryService.initializeStock(testProduct, null, 10, 2);
        testOrder.setOrderStatus("CANCELLED");
        testOrder.setPaymentStatus("PENDING_PAYMENT");
        orderRepository.save(testOrder);

        String razorpayOrderId = "order_verify_cancel_ok";
        String razorpayPaymentId = "pay_verify_cancel_ok";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String validSignature = signatureVerifier.calculateHmacSha256(
                razorpayOrderId + "|" + razorpayPaymentId, razorpayConfig.getKeySecret()
        );
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(), razorpayOrderId, razorpayPaymentId, validSignature
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")));

        Order updated = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("CONFIRMED", updated.getOrderStatus());
        assertEquals("COMPLETED", updated.getPaymentStatus());
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("NPZ-VERIFY-002: verifyPayment handles cancelled order with out-of-stock by setting PAYMENT_RECEIVED_OUT_OF_STOCK")
    void testVerifyPayment_CancelledOrder_OutOfStock_DoesNotConfirm() throws Exception {
        Product outProduct = productRepository.save(new Product(
                testProduct.getBrand(), testProduct.getCategory(), "Zero Stock Food", "food-zero", "SKU-ZERO",
                new BigDecimal("600.00"), new BigDecimal("600.00")
        ));
        inventoryService.initializeStock(outProduct, null, 0, 1);

        Order cancelledOrder = new Order();
        cancelledOrder.setOrderNumber("ORD-ZERO-" + UUID.randomUUID().toString().substring(0, 8));
        cancelledOrder.setCustomer(testCustomer);
        cancelledOrder.setOrderStatus("CANCELLED");
        cancelledOrder.setPaymentStatus("PENDING_PAYMENT");
        cancelledOrder.setPaymentMethod("RAZORPAY");
        cancelledOrder.setTotalAmount(new BigDecimal("600.00"));
        cancelledOrder.setDiscountAmount(BigDecimal.ZERO);
        cancelledOrder.setDeliveryCharge(BigDecimal.ZERO);
        cancelledOrder.setTaxAmount(BigDecimal.ZERO);
        cancelledOrder.setFinalPayableAmount(new BigDecimal("600.00"));
        cancelledOrder.setShopDistanceKm(new BigDecimal("1.20"));
        cancelledOrder.setIsWithinRadius(true);
        cancelledOrder.setDeliveryAddressJson("{}");
        cancelledOrder = orderRepository.save(cancelledOrder);

        OrderItem zeroItem = new OrderItem(cancelledOrder, outProduct, null, outProduct.getName(), "Brand", "SKU-ZERO", 1, new BigDecimal("600.00"), new BigDecimal("600.00"), BigDecimal.ZERO, BigDecimal.ZERO, "9503", "{}", "{}");
        cancelledOrder.getItems().add(zeroItem);
        orderItemRepository.save(zeroItem);
        cancelledOrder = orderRepository.save(cancelledOrder);

        String razorpayOrderId = "order_zero_stock";
        Payment payment = new Payment(cancelledOrder, razorpayOrderId, cancelledOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String validSignature = signatureVerifier.calculateHmacSha256(
                razorpayOrderId + "|pay_zero_stock", razorpayConfig.getKeySecret()
        );
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                cancelledOrder.getOrderNumber(), razorpayOrderId, "pay_zero_stock", validSignature
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("CANCELLED")));

        Order updated = orderRepository.findByOrderNumber(cancelledOrder.getOrderNumber()).orElseThrow();
        assertEquals("CANCELLED", updated.getOrderStatus());
        assertEquals("PAYMENT_RECEIVED_OUT_OF_STOCK", updated.getPaymentStatus());
    }

    @Test
    @DisplayName("NPZ-VERIFY-003: Monotonic transition: Late webhook does not regress PACKED or DISPATCHED order to CONFIRMED")
    void testMonotonicOrderTransition_LateWebhookDoesNotRegressStatus() throws Exception {
        testOrder.setOrderStatus("PACKED");
        orderRepository.save(testOrder);

        String razorpayOrderId = "order_late_packed";
        String razorpayPaymentId = "pay_late_packed";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""
                + razorpayPaymentId + "\",\"order_id\":\"" + razorpayOrderId + "\"}}}}";
        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Order updated = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("PACKED", updated.getOrderStatus(), "Status must remain PACKED and not regress to CONFIRMED");
        assertEquals("COMPLETED", updated.getPaymentStatus());
    }

    @Test
    @DisplayName("NPZ-VERIFY-003: Late payment.failed webhook does not overwrite successful captured payment")
    void testLatePaymentFailedWebhook_DoesNotOverwriteSuccess() throws Exception {
        String razorpayOrderId = "order_late_fail";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "SUCCESS", "RAZORPAY");
        payment.setGatewayPaymentId("pay_already_captured");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_failed_late\",\"order_id\":\""
                + razorpayOrderId + "\",\"error_description\":\"Bank timeout\"}}}}";
        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Payment checked = paymentRepository.findByGatewayOrderId(razorpayOrderId).orElseThrow();
        assertEquals("SUCCESS", checked.getStatus(), "Payment status must remain SUCCESS and not be overwritten by late failure event");
    }

    @Test
    @DisplayName("NPZ-VERIFY-004: Duplicate payment.captured webhook is idempotent and does not create duplicate invoices or status history")
    void testCapturedOrder_DuplicateWebhook_Idempotent() throws Exception {
        String razorpayOrderId = "order_dup_capture";
        String razorpayPaymentId = "pay_dup_capture";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""
                + razorpayPaymentId + "\",\"order_id\":\"" + razorpayOrderId + "\"}}}}";
        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        // First webhook
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        int statusHistoryCountAfterFirst = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(testOrder.getId()).size();
        int invoiceCountAfterFirst = invoiceRepository.findByOrderId(testOrder.getId()).isPresent() ? 1 : 0;

        // Duplicate webhook
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        int statusHistoryCountAfterSecond = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(testOrder.getId()).size();
        int invoiceCountAfterSecond = invoiceRepository.findByOrderId(testOrder.getId()).isPresent() ? 1 : 0;

        assertEquals(statusHistoryCountAfterFirst, statusHistoryCountAfterSecond, "Duplicate webhook must not append redundant status history entries");
        assertEquals(invoiceCountAfterFirst, invoiceCountAfterSecond, "Duplicate webhook must not create duplicate invoices");
    }

    @Test
    @DisplayName("NPZ-VERIFY-004: Duplicate webhook on PAYMENT_RECEIVED_OUT_OF_STOCK order does not retry reservation or append history")
    void testOutOfStockCapturedOrder_DuplicateWebhook_Idempotent() throws Exception {
        // Prepare out-of-stock product
        Product outProduct = productRepository.save(new Product(
                testProduct.getBrand(), testProduct.getCategory(), "Zero Food", "zero-food", "SKU-Z1",
                new BigDecimal("500.00"), new BigDecimal("500.00")
        ));
        inventoryService.initializeStock(outProduct, null, 0, 1);

        Order cancelledOrder = new Order();
        cancelledOrder.setOrderNumber("ORD-OUT-STOCK-" + UUID.randomUUID().toString().substring(0, 8));
        cancelledOrder.setCustomer(testCustomer);
        cancelledOrder.setOrderStatus("CANCELLED");
        cancelledOrder.setPaymentStatus("PENDING_PAYMENT");
        cancelledOrder.setPaymentMethod("RAZORPAY");
        cancelledOrder.setTotalAmount(new BigDecimal("500.00"));
        cancelledOrder.setDiscountAmount(BigDecimal.ZERO);
        cancelledOrder.setDeliveryCharge(BigDecimal.ZERO);
        cancelledOrder.setTaxAmount(BigDecimal.ZERO);
        cancelledOrder.setFinalPayableAmount(new BigDecimal("500.00"));
        cancelledOrder.setShopDistanceKm(new BigDecimal("1.20"));
        cancelledOrder.setIsWithinRadius(true);
        cancelledOrder.setDeliveryAddressJson("{}");
        cancelledOrder = orderRepository.save(cancelledOrder);

        OrderItem item = new OrderItem(cancelledOrder, outProduct, null, outProduct.getName(), "Brand", "SKU-Z1", 1, new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, "9503", "{}", "{}");
        cancelledOrder.getItems().add(item);
        orderItemRepository.save(item);
        cancelledOrder = orderRepository.save(cancelledOrder);

        String razorpayOrderId = "order_out_of_stock_dup";
        Payment payment = new Payment(cancelledOrder, razorpayOrderId, cancelledOrder.getFinalPayableAmount(), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_out_dup\",\"order_id\":\""
                + razorpayOrderId + "\"}}}}";
        String webhookSignature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        // First webhook -> transitions to PAYMENT_RECEIVED_OUT_OF_STOCK
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Order afterFirst = orderRepository.findByOrderNumber(cancelledOrder.getOrderNumber()).orElseThrow();
        assertEquals("PAYMENT_RECEIVED_OUT_OF_STOCK", afterFirst.getPaymentStatus());
        int historyCountAfterFirst = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(cancelledOrder.getId()).size();

        // Duplicate webhook -> must be safely ignored
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        int historyCountAfterSecond = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(cancelledOrder.getId()).size();
        assertEquals(historyCountAfterFirst, historyCountAfterSecond, "Duplicate capture event on out-of-stock order must not append duplicate history");
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("NPZ-VERIFY-005: Client verifyPayment returns idempotent success when order is already completed")
    void testClientVerifyPayment_AlreadyCompleted_IdempotentSuccess() throws Exception {
        testOrder.setPaymentStatus("COMPLETED");
        testOrder.setOrderStatus("CONFIRMED");
        orderRepository.save(testOrder);

        String razorpayOrderId = "order_already_comp";
        String razorpayPaymentId = "pay_already_comp";
        Payment payment = new Payment(testOrder, razorpayOrderId, testOrder.getFinalPayableAmount(), "SUCCESS", "RAZORPAY");
        payment.setGatewayPaymentId(razorpayPaymentId);
        paymentRepository.save(payment);

        String signature = signatureVerifier.calculateHmacSha256(razorpayOrderId + "|" + razorpayPaymentId, razorpayConfig.getKeySecret());
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                testOrder.getOrderNumber(), razorpayOrderId, razorpayPaymentId, signature
        );

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")));
    }

    @Test
    @DisplayName("NPZ-VERIFY-006: State Transition: DELIVERED order cannot regress to CONFIRMED or CANCELLED")
    void testTerminalOrderCannotRegressFromDelivered() {
        testOrder.setOrderStatus("DELIVERED");
        orderRepository.save(testOrder);

        assertThrows(IllegalStateException.class, () ->
                orderService.adminUpdateOrderStatus(testOrder.getId(), "CONFIRMED", "ADMIN", "Improper regression attempt")
        );
        assertThrows(IllegalStateException.class, () ->
                orderService.adminUpdateOrderStatus(testOrder.getId(), "CANCELLED", "ADMIN", "Improper cancellation attempt")
        );
    }

    @Test
    @DisplayName("NPZ-VERIFY-006: State Transition: CANCELLED order cannot transition to DELIVERED via admin update")
    void testTerminalOrderCannotRegressFromCancelled() {
        testOrder.setOrderStatus("CANCELLED");
        orderRepository.save(testOrder);

        assertThrows(IllegalStateException.class, () ->
                orderService.adminUpdateOrderStatus(testOrder.getId(), "DELIVERED", "ADMIN", "Improper delivery attempt without stock")
        );
    }

    @Test
    @DisplayName("NPZ-VERIFY-007: Exact Decimal Reconciliation: Line item totals, taxable, GST, discounts reconcile to exact paise")
    void testExactDecimalReconciliation() {
        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(testOrder);
        assertNotNull(dto);

        // Sum of item gross totals
        BigDecimal itemsGrossSum = dto.items().stream()
                .map(InvoiceDto.InvoiceItemDto::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sum of item taxable values + CGST + SGST
        BigDecimal itemsTaxableSum = dto.items().stream()
                .map(InvoiceDto.InvoiceItemDto::taxableValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemsCgstSum = dto.items().stream()
                .map(InvoiceDto.InvoiceItemDto::cgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemsSgstSum = dto.items().stream()
                .map(InvoiceDto.InvoiceItemDto::sgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal calculatedTotal = itemsTaxableSum.add(itemsCgstSum).add(itemsSgstSum)
                .add(dto.deliveryCharge())
                .subtract(dto.discountAmount());

        assertEquals(0, calculatedTotal.compareTo(dto.grandTotal()),
                "Calculated invoice total (" + calculatedTotal + ") must match grandTotal (" + dto.grandTotal() + ") to exact paise");
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("COD Transition: Cancelling pending Razorpay order releases reserved stock and marks status CANCELLED")
    void testCancelPendingRazorpayOrder_Success_ReleasesInventory() throws Exception {
        inventoryService.initializeStock(testProduct, null, 10, 2);
        inventoryService.reserveStock(testProduct.getId(), null, 1, testOrder.getOrderNumber());
        testOrder.setOrderStatus("PENDING_PAYMENT");
        testOrder.setPaymentStatus("PENDING");
        orderRepository.save(testOrder);

        int initialAvailable = inventoryService.getAvailableStock(testProduct.getId(), null);
        com.novapetzlife.returns.dto.CancelOrderRequest request = new com.novapetzlife.returns.dto.CancelOrderRequest("Switched to Cash on Delivery during checkout");

        mockMvc.perform(post("/api/v1/orders/" + testOrder.getOrderNumber() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CANCELLED")));

        Order updated = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();
        assertEquals("CANCELLED", updated.getOrderStatus());

        int newAvailable = inventoryService.getAvailableStock(testProduct.getId(), null);
        assertEquals(initialAvailable + 1, newAvailable, "Cancelling pending Razorpay order must release reserved stock back to available inventory");
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("COD Transition: Repeated cancellation on already cancelled order is idempotent")
    void testCancelOrder_RepeatedCancellation_IsIdempotent() throws Exception {
        testOrder.setOrderStatus("CANCELLED");
        orderRepository.save(testOrder);

        com.novapetzlife.returns.dto.CancelOrderRequest request = new com.novapetzlife.returns.dto.CancelOrderRequest("Repeated cancel attempt");

        mockMvc.perform(post("/api/v1/orders/" + testOrder.getOrderNumber() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CANCELLED")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("COD Transition: createPaymentOrder on a CANCELLED order is strictly rejected")
    void testPaymentOrderCreation_CancelledOrder_Rejected() throws Exception {
        testOrder.setOrderStatus("CANCELLED");
        orderRepository.save(testOrder);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is cancelled and ineligible for payment")));
    }

    @Test
    @WithMockUser(username = "aditya.payment@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("COD Transition: createPaymentOrder on order with existing SUCCESS payment is strictly rejected")
    void testPaymentOrderCreation_CompletedOrder_Rejected() throws Exception {
        testOrder.setPaymentStatus("COMPLETED");
        orderRepository.save(testOrder);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(testOrder.getOrderNumber());

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is already paid")));
    }
}
