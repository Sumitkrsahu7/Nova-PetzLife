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
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.dto.CreatePaymentOrderRequest;
import com.novapetzlife.payment.dto.VerifyPaymentRequest;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.tax.InvoiceRepository;
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
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private RazorpaySignatureVerifier signatureVerifier;

    @Autowired
    private RazorpayConfig razorpayConfig;

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

    private Order testOrder;
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
        Customer customer = new Customer(null, "aditya." + suffix + "@example.com", "+9198765" + suffix, "Aditya", "Menon", true);
        customer = customerRepository.save(customer);

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
    }

    @Test
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
    @DisplayName("Should verify valid HMAC-SHA256 signature, mark order CONFIRMED, and generate invoice")
    void testVerifyPayment_ValidSignature_ConfirmsOrderAndGeneratesInvoice() throws Exception {
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
    @DisplayName("Should reject invalid signature with 400 Bad Request and leave order unconfirmed")
    void testVerifyPayment_InvalidSignature_Fails() throws Exception {
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
    }

    @Test
    @DisplayName("Should verify simulated dev signature successfully in test profile")
    void testVerifyPayment_SimulatedDevSignature_Succeeds() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")));
    }

    @Test
    @DisplayName("Should retrieve GST tax invoice with Kerala State Code 32, store GSTIN, and HSN breakdown")
    void testGetOrderGstInvoice_Success() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + testOrder.getOrderNumber() + "/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.storeName", is("NOVA PETZLIFE")))
                .andExpect(jsonPath("$.data.storeStateCode", is("32")))
                .andExpect(jsonPath("$.data.storeGstin", is("32AAAAA0000A1Z5")))
                .andExpect(jsonPath("$.data.placeOfSupply", is("Kerala (32)")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].hsnCode", is("2309")))
                .andExpect(jsonPath("$.data.items[0].cgstRate", is(9.00)))
                .andExpect(jsonPath("$.data.items[0].sgstRate", is(9.00)))
                .andExpect(jsonPath("$.data.amountInWords", containsString("Rupees")));
    }

    @Test
    @DisplayName("Should process Razorpay webhook payment.captured with valid signature")
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
}
