package com.novapetzlife.payment;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.inventory.Inventory;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.order.OrderStatusHistoryRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.returns.ReturnService;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Concurrency, race-condition and atomicity verification suite for Razorpay payment capture
 * and order cancellation (including switching to Cash on Delivery).
 *
 * Covers all 8 mandatory safety scenarios:
 * 1. Cancellation completes before payment capture.
 * 2. Payment capture completes before cancellation.
 * 3. Cancellation and payment capture execute concurrently.
 * 4. Duplicate captured webhook arrives during cancellation.
 * 5. Cancellation is attempted after payment has succeeded.
 * 6. Payment capture arrives after cancellation has completed (COD vs non-COD recovery).
 * 7. Inventory remains strictly consistent in each scenario.
 * 8. No two orders become eligible for fulfillment for the same customer purchase transition.
 */
@SpringBootTest
@ActiveProfiles("test")
public class PaymentCancellationConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private RazorpayConfig razorpayConfig;

    @Autowired
    private RazorpaySignatureVerifier signatureVerifier;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReturnRequestItemRepository returnRequestItemRepository;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private ReturnInspectionRepository returnInspectionRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private RazorpayClientService razorpayClientService;

    private User testUser;
    private Customer testCustomer;
    private Brand testBrand;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        returnInspectionRepository.deleteAll();
        returnRequestItemRepository.deleteAll();
        returnRequestRepository.deleteAll();
        refundRepository.deleteAll();
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        inventoryRepository.deleteAll();

        when(razorpayClientService.isConfigured()).thenReturn(true);
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_conc_mock_123", "processed", 50000L));

        testUser = userRepository.findByEmail("concurrency.test@example.com").orElseGet(() ->
                userRepository.save(new User("concurrency.test@example.com", "Password@123", "Conc", "User", "+919876543210"))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(testUser);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        testBrand = brandRepository.save(new Brand("ConcBrand-" + suffix, "slug-brand-" + suffix, "Brand"));
        testCategory = categoryRepository.save(new Category("ConcCat-" + suffix, "slug-cat-" + suffix, "Cat", null, 1));
    }

    private Order createTestOrderWithReservedStock(String orderNumber, String gatewayOrderId) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand,
                testCategory,
                "Conc Product " + suffix,
                "slug-prod-" + suffix,
                "SKU-" + suffix.toUpperCase(),
                new BigDecimal("500.00"),
                new BigDecimal("500.00")
        );
        product = productRepository.save(product);

        // 10 initial stock, 0 reserved initially
        inventoryService.initializeStock(product, null, 10, 2);
        // Reserve 1 unit for checkout
        inventoryService.reserveStock(product.getId(), null, 1, orderNumber);

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(testCustomer);
        order.setOrderStatus("PENDING_PAYMENT");
        order.setPaymentStatus("PENDING");
        order.setPaymentMethod("RAZORPAY");
        order.setTotalAmount(new BigDecimal("500.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setDeliveryCharge(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setFinalPayableAmount(new BigDecimal("500.00"));
        order.setDeliveryAddressJson("{\"addressLine1\":\"Pattom\"}");
        order.setShopDistanceKm(new BigDecimal("1.0"));
        order.setIsWithinRadius(true);

        OrderItem item = new OrderItem(
                order,
                product,
                null,
                product.getName(),
                testBrand.getName(),
                product.getSku(),
                1,
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "2309",
                "{}",
                "{}"
        );
        order.addItem(item);
        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment(savedOrder, gatewayOrderId, new BigDecimal("500.00"), "CREATED", "RAZORPAY");
        payment.setCurrency("INR");
        paymentRepository.save(payment);

        return savedOrder;
    }

    private String createPaymentCapturedPayload(String gatewayOrderId, String paymentId, long amountPaise) {
        return "{\n" +
                "  \"entity\": \"event\",\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"contains\": [\"payment\"],\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + paymentId + "\",\n" +
                "        \"order_id\": \"" + gatewayOrderId + "\",\n" +
                "        \"amount\": " + amountPaise + ",\n" +
                "        \"status\": \"captured\",\n" +
                "        \"currency\": \"INR\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private void dispatchWebhook(String gatewayOrderId, String paymentId, long amountPaise) throws Exception {
        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, amountPaise);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());
        paymentService.handleWebhook(payload, signature);
    }

    @Test
    @DisplayName("Scenario 1: Cancellation completes before payment capture")
    void testScenario1_CancellationCompletesBeforePaymentCapture() throws Exception {
        String orderNumber = "ORD-SCENARIO-1";
        String gatewayOrderId = "order_gw_scen_1";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);
        Long productId = order.getItems().get(0).getProduct().getId();

        // Before cancellation: available = 9, reserved = 1
        assertEquals(9, inventoryService.getAvailableStock(productId, null));

        // 1. Customer cancels order to switch to COD
        returnService.cancelOrder(orderNumber, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);

        Order cancelledOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getOrderStatus());
        // Inventory reservation released: available is back to 10
        assertEquals(10, inventoryService.getAvailableStock(productId, null));

        // 2. Late payment.captured webhook arrives for the cancelled order
        dispatchWebhook(gatewayOrderId, "pay_late_1", 50000L);

        // Assertions:
        Order postPaymentOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        // Crucial: Must NOT revive the order to CONFIRMED! Order remains CANCELLED.
        assertEquals("CANCELLED", postPaymentOrder.getOrderStatus());
        // Stock must NOT be re-reserved: stays 10 available
        assertEquals(10, inventoryService.getAvailableStock(productId, null));
        // Payment record marked SUCCESS or REFUNDED
        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
        assertTrue("SUCCESS".equalsIgnoreCase(payment.getStatus()) || "REFUNDED".equalsIgnoreCase(payment.getStatus()),
                "Payment status should be SUCCESS or REFUNDED, was: " + payment.getStatus());
        // Automatic refund must be initiated
        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertFalse(refunds.isEmpty(), "Automatic refund must be recorded for payment captured on COD-cancelled order");
    }

    @Test
    @DisplayName("Scenario 2: Payment capture completes before cancellation")
    void testScenario2_PaymentCaptureCompletesBeforeCancellation() throws Exception {
        String orderNumber = "ORD-SCENARIO-2";
        String gatewayOrderId = "order_gw_scen_2";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);
        Long productId = order.getItems().get(0).getProduct().getId();

        // 1. Payment captured first
        dispatchWebhook(gatewayOrderId, "pay_prior_2", 50000L);

        Order paidOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CONFIRMED", paidOrder.getOrderStatus());
        assertEquals("COMPLETED", paidOrder.getPaymentStatus());

        // 2. Cancellation request arrives after payment capture
        returnService.cancelOrder(orderNumber, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);

        Order cancelledOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getOrderStatus());
        // Inventory released back to available
        assertEquals(10, inventoryService.getAvailableStock(productId, null));
        // Refund initiated
        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertFalse(refunds.isEmpty(), "Refund must be recorded when paid order is cancelled");
    }

    @Test
    @DisplayName("Scenario 3: Cancellation and payment capture execute concurrently")
    void testScenario3_ConcurrentCancellationAndPaymentCapture() throws Exception {
        String orderNumber = "ORD-SCENARIO-3";
        String gatewayOrderId = "order_gw_scen_3";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);
        Long productId = order.getItems().get(0).getProduct().getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Void> cancelTask = () -> {
            startLatch.await();
            returnService.cancelOrder(orderNumber, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);
            return null;
        };

        Callable<Void> paymentTask = () -> {
            startLatch.await();
            dispatchWebhook(gatewayOrderId, "pay_concurrent_3", 50000L);
            return null;
        };

        Future<Void> future1 = executor.submit(cancelTask);
        Future<Void> future2 = executor.submit(paymentTask);

        // Fire both threads simultaneously
        startLatch.countDown();

        future1.get(10, TimeUnit.SECONDS);
        future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        // Since cancellation was requested, final state must be CANCELLED (never left in half-confirmed limbo)
        assertEquals("CANCELLED", finalOrder.getOrderStatus());

        // Stock must be 10 available, 0 reserved
        assertEquals(10, inventoryService.getAvailableStock(productId, null));

        // Refund must be initiated (either from cancelOrder or from applyCapturedPayment)
        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertFalse(refunds.isEmpty(), "Refund must be initiated under concurrent race condition");
    }

    @Test
    @DisplayName("Scenario 4: Duplicate captured webhook arrives during cancellation")
    void testScenario4_DuplicateCapturedWebhookDuringCancellation() throws Exception {
        String orderNumber = "ORD-SCENARIO-4";
        String gatewayOrderId = "order_gw_scen_4";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);

        returnService.cancelOrder(orderNumber, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);

        // First webhook
        dispatchWebhook(gatewayOrderId, "pay_dup_4", 50000L);
        // Duplicate webhook
        dispatchWebhook(gatewayOrderId, "pay_dup_4", 50000L);

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", finalOrder.getOrderStatus());

        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        // Exactly one refund should be created, duplicate webhook must be idempotent
        assertEquals(1, refunds.size(), "Duplicate webhook must be idempotent and not issue duplicate refunds");
    }

    @Test
    @DisplayName("Scenario 5: Cancellation is attempted after payment has succeeded")
    void testScenario5_CancellationAttemptedAfterPaymentSucceeded() throws Exception {
        String orderNumber = "ORD-SCENARIO-5";
        String gatewayOrderId = "order_gw_scen_5";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);
        Long productId = order.getItems().get(0).getProduct().getId();

        // Payment succeeds
        dispatchWebhook(gatewayOrderId, "pay_succ_5", 50000L);

        // Order is now CONFIRMED and COMPLETED
        Order paidOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CONFIRMED", paidOrder.getOrderStatus());
        assertEquals("COMPLETED", paidOrder.getPaymentStatus());

        // Customer cancels order
        returnService.cancelOrder(orderNumber, "Customer changed mind before packing", "CUSTOMER", true);

        Order cancelledOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getOrderStatus());
        assertEquals(10, inventoryService.getAvailableStock(productId, null));

        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertFalse(refunds.isEmpty(), "Refund must be recorded for cancelled paid order");
    }

    @Test
    @DisplayName("Scenario 6: Payment capture arrives after non-COD cancellation (Late Payment Recovery vs OOS)")
    void testScenario6_PaymentCaptureAfterNonCodCancellation() throws Exception {
        // 6A: Stock is available -> Revived to CONFIRMED
        String orderNumberA = "ORD-SCENARIO-6A";
        String gatewayOrderIdA = "order_gw_scen_6A";
        Order orderA = createTestOrderWithReservedStock(orderNumberA, gatewayOrderIdA);
        Long productIdA = orderA.getItems().get(0).getProduct().getId();

        returnService.cancelOrder(orderNumberA, "Payment session timeout", "SYSTEM", false);
        assertEquals(10, inventoryService.getAvailableStock(productIdA, null));

        // Late payment arrives for non-COD cancellation
        dispatchWebhook(gatewayOrderIdA, "pay_late_6A", 50000L);

        Order revivedOrder = orderRepository.findByOrderNumber(orderNumberA).orElseThrow();
        assertEquals("CONFIRMED", revivedOrder.getOrderStatus());
        assertEquals("COMPLETED", revivedOrder.getPaymentStatus());
        // Re-reserved: available is 9
        assertEquals(9, inventoryService.getAvailableStock(productIdA, null));

        // 6B: Stock is OUT OF STOCK -> Marked PAYMENT_RECEIVED_OUT_OF_STOCK
        String orderNumberB = "ORD-SCENARIO-6B";
        String gatewayOrderIdB = "order_gw_scen_6B";
        Order orderB = createTestOrderWithReservedStock(orderNumberB, gatewayOrderIdB);
        Long productIdB = orderB.getItems().get(0).getProduct().getId();

        returnService.cancelOrder(orderNumberB, "Payment session timeout", "SYSTEM", false);

        // Drain stock to 0
        Inventory inv = inventoryRepository.findByProductIdAndVariantIsNull(productIdB).orElseThrow();
        inv.setAvailableStock(0);
        inv.setCurrentStock(0);
        inventoryRepository.save(inv);

        dispatchWebhook(gatewayOrderIdB, "pay_late_6B", 50000L);

        Order oosOrder = orderRepository.findByOrderNumber(orderNumberB).orElseThrow();
        assertEquals("CANCELLED", oosOrder.getOrderStatus());
        assertEquals("PAYMENT_RECEIVED_OUT_OF_STOCK", oosOrder.getPaymentStatus());
    }

    @Test
    @DisplayName("Scenario 7: Inventory remains consistent across all transitions")
    void testScenario7_InventoryConservationInvariant() throws Exception {
        String orderNumber = "ORD-SCENARIO-7";
        String gatewayOrderId = "order_gw_scen_7";
        Order order = createTestOrderWithReservedStock(orderNumber, gatewayOrderId);
        Long productId = order.getItems().get(0).getProduct().getId();

        Inventory invBefore = inventoryRepository.findByProductIdAndVariantIsNull(productId).orElseThrow();
        assertEquals(invBefore.getCurrentStock(), invBefore.getAvailableStock() + invBefore.getReservedStock());

        // Cancel
        returnService.cancelOrder(orderNumber, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);
        Inventory invAfterCancel = inventoryRepository.findByProductIdAndVariantIsNull(productId).orElseThrow();
        assertEquals(invAfterCancel.getCurrentStock(), invAfterCancel.getAvailableStock() + invAfterCancel.getReservedStock());
        assertEquals(0, invAfterCancel.getReservedStock());

        // Webhook
        dispatchWebhook(gatewayOrderId, "pay_inv_7", 50000L);
        Inventory invAfterWebhook = inventoryRepository.findByProductIdAndVariantIsNull(productId).orElseThrow();
        assertEquals(invAfterWebhook.getCurrentStock(), invAfterWebhook.getAvailableStock() + invAfterWebhook.getReservedStock());
        assertEquals(0, invAfterWebhook.getReservedStock());
    }

    @Test
    @DisplayName("Scenario 8: No two orders become eligible for fulfillment for the same customer purchase transition")
    void testScenario8_NoTwoOrdersEligibleForFulfillment() throws Exception {
        // Step 1: Customer initiates Order A with Razorpay
        String orderNumberA = "ORD-CUSTOMER-PURCHASE-A";
        String gatewayOrderIdA = "order_gw_purchase_A";
        Order orderA = createTestOrderWithReservedStock(orderNumberA, gatewayOrderIdA);
        Long productIdA = orderA.getItems().get(0).getProduct().getId();

        // Step 2: Customer closes Razorpay and switches to COD
        // Checkout calls cancelOrder on Order A (fail-closed)
        returnService.cancelOrder(orderNumberA, "Switched to Cash on Delivery during checkout", "CUSTOMER", true);
        Order updatedA = orderRepository.findByOrderNumber(orderNumberA).orElseThrow();
        assertEquals("CANCELLED", updatedA.getOrderStatus());

        // Step 3: Frontend creates new COD Order B
        String orderNumberB = "ORD-CUSTOMER-PURCHASE-B";
        inventoryService.reserveStock(productIdA, null, 1, orderNumberB);

        Order orderB = new Order();
        orderB.setOrderNumber(orderNumberB);
        orderB.setCustomer(testCustomer);
        orderB.setOrderStatus("CONFIRMED"); // COD orders are immediately CONFIRMED
        orderB.setPaymentStatus("PENDING");
        orderB.setPaymentMethod("COD");
        orderB.setTotalAmount(new BigDecimal("500.00"));
        orderB.setDiscountAmount(BigDecimal.ZERO);
        orderB.setDeliveryCharge(BigDecimal.ZERO);
        orderB.setTaxAmount(BigDecimal.ZERO);
        orderB.setFinalPayableAmount(new BigDecimal("500.00"));
        orderB.setDeliveryAddressJson("{\"addressLine1\":\"Pattom\"}");
        orderB.setShopDistanceKm(new BigDecimal("1.0"));
        orderB.setIsWithinRadius(true);
        orderRepository.save(orderB);

        // Step 4: Late payment.captured webhook arrives for Order A
        dispatchWebhook(gatewayOrderIdA, "pay_late_purchase_A", 50000L);

        // Step 5: Verification of fulfillment eligibility
        Order finalA = orderRepository.findByOrderNumber(orderNumberA).orElseThrow();
        Order finalB = orderRepository.findByOrderNumber(orderNumberB).orElseThrow();

        // Order A must NOT be fulfillable
        assertEquals("CANCELLED", finalA.getOrderStatus(), "Order A must remain CANCELLED");
        assertNotEquals("CONFIRMED", finalA.getOrderStatus());

        // Order B is the only fulfillable order
        assertEquals("CONFIRMED", finalB.getOrderStatus(), "Order B must be CONFIRMED");

        // Count fulfillable orders across this purchase transition
        long fulfillableCount = List.of(finalA, finalB).stream()
                .filter(o -> "CONFIRMED".equalsIgnoreCase(o.getOrderStatus())
                        || "PROCESSING".equalsIgnoreCase(o.getOrderStatus())
                        || "SHIPPED".equalsIgnoreCase(o.getOrderStatus())
                        || "OUT_FOR_DELIVERY".equalsIgnoreCase(o.getOrderStatus()))
                .count();

        assertEquals(1, fulfillableCount, "Exactly ONE order must be eligible for fulfillment for this purchase transition");
    }
}
