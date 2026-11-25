package com.novapetzlife.payment;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
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
import com.novapetzlife.refund.RefundRecoveryService;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.refund.RefundService;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Targeted verification suite for durable refund dispatch, crash recovery,
 * reconciliation-first gateway checks, and bounded retries.
 *
 * Scenarios:
 * 1. Application restart after intent commit but before callback execution.
 * 2. Recovery worker dispatches a genuinely undispatched intent.
 * 3. Concurrent recovery workers cannot dispatch the same intent.
 * 4. Crash after gateway acceptance but before local gateway ID persistence.
 * 5. Uncertain gateway outcome is not blindly retried.
 * 6. Duplicate capture webhook while recovery is running.
 * 7. Successful webhook reconciliation of an uncertain request.
 * 8. Refund remains auditable if recovery cannot establish its gateway outcome.
 */
@SpringBootTest
@ActiveProfiles("test")
public class RefundCrashRecoveryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundRecoveryService refundRecoveryService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

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
        when(razorpayClientService.fetchRefundsForPayment(anyString())).thenReturn(List.of());
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_123", "processed", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_123", "processed", 50000L));

        testUser = userRepository.findByEmail("recovery.test@example.com").orElseGet(() ->
                userRepository.save(new User("recovery.test@example.com", "Password@123", "Recovery", "Tester", "+919876543210"))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(testUser);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        testBrand = brandRepository.save(new Brand("RecBrand-" + suffix, "slug-recbrand-" + suffix, "Brand"));
        testCategory = categoryRepository.save(new Category("RecCat-" + suffix, "slug-reccat-" + suffix, "Cat", null, 1));
    }

    private Order createCancelledCodSwitchOrder(String orderNumber, String gatewayOrderId, String paymentId) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand,
                testCategory,
                "Rec Product " + suffix,
                "slug-recprod-" + suffix,
                "SKU-REC-" + suffix.toUpperCase(),
                new BigDecimal("500.00"),
                new BigDecimal("500.00")
        );
        product = productRepository.save(product);

        inventoryService.initializeStock(product, null, 10, 2);

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(testCustomer);
        order.setOrderStatus("CANCELLED");
        order.setCancellationReason("Switched to Cash on Delivery during checkout");
        order.setPaymentStatus("REFUND_PROCESSING");
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

        Payment payment = new Payment(savedOrder, gatewayOrderId, new BigDecimal("500.00"), "SUCCESS", "RAZORPAY");
        payment.setGatewayPaymentId(paymentId);
        payment.setCurrency("INR");
        paymentRepository.save(payment);

        return savedOrder;
    }

    private String createRefundProcessedPayload(String refundId, String paymentId, long amountPaise) {
        return "{\n" +
                "  \"entity\": \"event\",\n" +
                "  \"event\": \"refund.processed\",\n" +
                "  \"payload\": {\n" +
                "    \"refund\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + refundId + "\",\n" +
                "        \"payment_id\": \"" + paymentId + "\",\n" +
                "        \"amount\": " + amountPaise + ",\n" +
                "        \"status\": \"processed\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
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
                "        \"entity\": \"payment\",\n" +
                "        \"amount\": " + amountPaise + ",\n" +
                "        \"currency\": \"INR\",\n" +
                "        \"status\": \"captured\",\n" +
                "        \"order_id\": \"" + gatewayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    @Test
    @DisplayName("Scenario 1: Application restart after intent commit but before callback execution recovers cleanly")
    void test1_RestartAfterIntentCommitBeforeCallback_RecoveredCleanly() {
        String orderNumber = "ORD-REC-001-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_rec_001";
        String paymentId = "pay_rec_001";
        Order order = createCancelledCodSwitchOrder(orderNumber, gatewayOrderId, paymentId);

        // Simulate crash right after DB commit: intent is saved with INITIATED, but afterCommit never ran
        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Simulated crash before afterCommit", "SYSTEM");
        assertNotNull(intent.getId());
        assertEquals("INITIATED", intent.getStatus(), "Committed intent must start in INITIATED state");
        assertNull(intent.getGatewayRefundId(), "Gateway refund ID must be null prior to dispatch");

        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_rec_001", "processed", 50000L));

        // Recovery worker runs upon restart (using threshold of future/now to capture candidate)
        int recoveredCount = refundRecoveryService.processPendingRefundRecovery(Instant.now().plusSeconds(5), 3);
        assertEquals(1, recoveredCount, "Recovery worker must process the stranded INITIATED intent");

        Refund recovered = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", recovered.getStatus(), "Refund must transition to SUCCESS upon recovery dispatch");
        assertEquals("rfnd_rec_001", recovered.getGatewayRefundId());

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("REFUNDED", finalOrder.getPaymentStatus());
        assertEquals("CANCELLED", finalOrder.getOrderStatus());
    }

    @Test
    @DisplayName("Scenario 2: Recovery worker dispatches a genuinely undispatched intent after verifying Razorpay has no prior refund")
    void test2_RecoveryWorkerDispatchesGenuinelyUndispatchedIntent() {
        String orderNumber = "ORD-REC-002-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_rec_002";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_rec_002", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Undispatched intent", "SYSTEM");

        // Reconciliation check confirms Razorpay has 0 refunds for this payment
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_rec_002", "processed", 50000L));

        boolean result = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(result);

        verify(razorpayClientService, times(1)).fetchRefundsForPayment(paymentId);
        verify(razorpayClientService, times(1)).createRefund(eq(paymentId), eq(50000L), anyString(), any());

        Refund updated = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", updated.getStatus());
        assertEquals("rfnd_rec_002", updated.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 3: Concurrent recovery workers cannot dispatch the same intent twice")
    void test3_ConcurrentRecoveryWorkersCannotDispatchSameIntent() throws Exception {
        String orderNumber = "ORD-REC-003-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_rec_003";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_rec_003", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Concurrent test", "SYSTEM");

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_rec_003", "processed", 50000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 1 gateway refund call must have occurred despite concurrent worker execution
        verify(razorpayClientService, times(1)).createRefund(eq(paymentId), eq(50000L), anyString(), any());

        Refund finalRefund = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", finalRefund.getStatus());
    }

    @Test
    @DisplayName("Scenario 4: Crash after gateway acceptance reconciles via fetchRefunds without duplicate disbursement")
    void test4_CrashAfterGatewayAcceptance_ReconcilesWithoutDuplicateDisbursement() {
        String orderNumber = "ORD-REC-004-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_rec_004";
        String gatewayRefundId = "rfnd_already_created_004";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_rec_004", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Crash after gateway accept", "SYSTEM");

        // Simulate Razorpay already accepted the refund before crash
        String receipt = "rfnd_" + orderNumber + "_" + intent.getId();
        when(razorpayClientService.fetchRefundsForPayment(paymentId))
                .thenReturn(List.of(new RazorpayClientService.GatewayPaymentRefundItem(
                        gatewayRefundId, 50000L, "processed", receipt
                )));

        boolean result = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(result);

        // MUST NOT call createRefund!
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any());
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString());

        Refund reconciled = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", reconciled.getStatus(), "Must reconcile to SUCCESS using existing gateway refund ID");
        assertEquals(gatewayRefundId, reconciled.getGatewayRefundId());

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("REFUNDED", finalOrder.getPaymentStatus());
    }

    @Test
    @DisplayName("Scenario 5: Uncertain gateway outcome is not blindly retried if Razorpay already processed it")
    void test5_UncertainGatewayOutcome_NotBlindlyRetried() {
        String orderNumber = "ORD-REC-005-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_rec_005";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_rec_005", paymentId);

        // Simulate uncertain timeout state: status is PROCESSING, gatewayRefundId is null
        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Timeout uncertain", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent.setReason("Automatic refund: late payment received [Network timeout: awaiting webhook reconciliation]");
        intent = refundRepository.saveAndFlush(intent);

        // Razorpay already has the refund
        when(razorpayClientService.fetchRefundsForPayment(paymentId))
                .thenReturn(List.of(new RazorpayClientService.GatewayPaymentRefundItem(
                        "rfnd_timeout_rec_005", 50000L, "processed", "rfnd_" + orderNumber + "_" + intent.getId()
                )));

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered);

        // Proves ZERO duplicate refund call made!
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any());
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString());

        Refund finalRefund = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", finalRefund.getStatus());
        assertEquals("rfnd_timeout_rec_005", finalRefund.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 6: Duplicate capture webhook while recovery is running is safely deduplicated")
    void test6_DuplicateCaptureWebhookWhileRecoveryRunning_DeduplicatedSafely() throws Exception {
        String orderNumber = "ORD-REC-006-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_rec_006";
        String paymentId = "pay_rec_006";
        Order order = createCancelledCodSwitchOrder(orderNumber, gatewayOrderId, paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Running recovery", "SYSTEM");

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        // Concurrent execution: recovery worker running while duplicate capture webhook arrives
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> webhookFuture = executor.submit(() -> {
            try {
                paymentService.handleWebhook(payload, signature);
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        Future<Boolean> recoveryFuture = executor.submit(() -> {
            return refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        });

        webhookFuture.get(10, TimeUnit.SECONDS);
        recoveryFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertEquals(1, refunds.size(), "Must never produce duplicate refund intents");
    }

    @Test
    @DisplayName("Scenario 7: Successful webhook reconciliation of an uncertain request updates status to SUCCESS")
    void test7_SuccessfulWebhookReconciliationOfUncertainRequest() throws Exception {
        String orderNumber = "ORD-REC-007-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_rec_007";
        String paymentId = "pay_rec_007";
        String gatewayRefundId = "rfnd_webhook_007";
        Order order = createCancelledCodSwitchOrder(orderNumber, gatewayOrderId, paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Awaiting webhook", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent.setReason("Payment refund [Network timeout: awaiting webhook reconciliation]");
        refundRepository.saveAndFlush(intent);

        String webhookPayload = createRefundProcessedPayload(gatewayRefundId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(webhookPayload, razorpayConfig.getWebhookSecret());

        paymentService.handleWebhook(webhookPayload, signature);

        Refund reconciled = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", reconciled.getStatus());
        assertEquals(gatewayRefundId, reconciled.getGatewayRefundId());
        assertNotNull(reconciled.getCompletedAt());

        Order updatedOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("REFUNDED", updatedOrder.getPaymentStatus());
    }

    @Test
    @DisplayName("Scenario 8: Refund remains auditable if recovery cannot establish gateway outcome after max attempts")
    void test8_RefundRemainsAuditableIfRecoveryCannotEstablishOutcome() {
        String orderNumber = "ORD-REC-008-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_rec_008";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_rec_008", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Uncertain gateway", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent.setReason("Refund [Network timeout] [Recovery attempt 3/3]");
        intent = refundRepository.saveAndFlush(intent);

        // Attempt recovery when max attempts already reached
        boolean processed = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertFalse(processed, "Must not attempt further automatic dispatch when max attempts reached");

        Refund abandoned = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PENDING_MANUAL_REFUND", abandoned.getStatus(), "Must escalate to PENDING_MANUAL_REFUND");
        assertTrue(abandoned.getReason().contains("manual intervention required"), "Reason must clearly document need for manual audit");

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", finalOrder.getOrderStatus(), "Order must strictly remain CANCELLED");
    }
}
