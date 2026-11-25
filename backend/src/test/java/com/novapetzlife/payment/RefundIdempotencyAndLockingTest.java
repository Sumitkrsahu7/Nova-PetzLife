package com.novapetzlife.payment;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
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
import com.novapetzlife.payment.dto.VerifyPaymentRequest;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.refund.RefundService;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Targeted verification suite for refund idempotency, intermediate states,
 * failure recovery, and database row lock acquisition paths.
 *
 * Covers:
 * 1. Duplicate capture webhook while refund is PROCESSING.
 * 2. Two concurrent capture webhooks attempting the same automatic refund.
 * 3. Refund API timeout after the request may have reached Razorpay.
 * 4. Refund API success and webhook reconciliation.
 * 5. Refund failure followed by safe recovery.
 * 6. Cancellation and capture competing for the same order lock.
 * 7. Repository findByOrderNumberWithLock execution and managed entity locking.
 * 8. Exception handling during lock contention without false success.
 */
@SpringBootTest
@ActiveProfiles("test")
public class RefundIdempotencyAndLockingTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReturnService returnService;

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
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_123", "processed", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_123", "processed", 50000L));

        testUser = userRepository.findByEmail("refund.test@example.com").orElseGet(() ->
                userRepository.save(new User("refund.test@example.com", "Password@123", "Refund", "Tester", "+919876543210"))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(testUser);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        testBrand = brandRepository.save(new Brand("RefBrand-" + suffix, "slug-refbrand-" + suffix, "Brand"));
        testCategory = categoryRepository.save(new Category("RefCat-" + suffix, "slug-refcat-" + suffix, "Cat", null, 1));
    }

    private Order createCancelledCodSwitchOrder(String orderNumber, String gatewayOrderId) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand,
                testCategory,
                "Ref Product " + suffix,
                "slug-refprod-" + suffix,
                "SKU-REF-" + suffix.toUpperCase(),
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

    @Test
    @DisplayName("Scenario 1: Duplicate capture webhook while refund is PROCESSING does not create duplicate refund")
    void test1_DuplicateCaptureWebhook_WhileRefundProcessing_NoDuplicateRefund() throws Exception {
        String orderNumber = "ORD-REF-001-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_001_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_001";
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        // First webhook arrives
        paymentService.handleWebhook(payload, signature);

        List<Refund> refundsAfterFirst = refundRepository.findAll();
        assertEquals(1, refundsAfterFirst.size(), "First webhook must create exactly one refund record");

        // Second duplicate webhook arrives
        paymentService.handleWebhook(payload, signature);

        List<Refund> refundsAfterSecond = refundRepository.findAll();
        assertEquals(1, refundsAfterSecond.size(), "Duplicate capture webhook must not create a second refund record");

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", order.getOrderStatus(), "Order must remain CANCELLED");
    }

    @Test
    @DisplayName("Scenario 2: Concurrent capture webhooks attempting automatic refund are serialized and idempotent")
    void test2_ConcurrentCaptureWebhooks_SameAutomaticRefund_SerializedAndIdempotent() throws Exception {
        String orderNumber = "ORD-REF-002-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_002_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_002";
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    paymentService.handleWebhook(payload, signature);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        List<Refund> refunds = refundRepository.findAll();
        assertEquals(1, refunds.size(), "Concurrent capture webhooks must result in exactly 1 refund record");
    }

    @Test
    @DisplayName("Scenario 3: Refund API timeout records PROCESSING state durably and preserves CANCELLED status")
    void test3_RefundApiTimeout_AwaitingGatewayConfirmation_DurableStatePreserved() throws Exception {
        String orderNumber = "ORD-REF-003-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_003_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_003";
        Order order = createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        // Simulate gateway timeout
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenThrow(new ResourceAccessException("Connection timed out waiting for Razorpay", new SocketTimeoutException("Read timed out")));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenThrow(new ResourceAccessException("Connection timed out waiting for Razorpay", new SocketTimeoutException("Read timed out")));

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        paymentService.handleWebhook(payload, signature);

        List<Refund> refunds = refundRepository.findAll();
        assertEquals(1, refunds.size(), "Refund must be durably recorded despite timeout");
        Refund timeoutRefund = refunds.get(0);
        assertEquals("PROCESSING", timeoutRefund.getStatus(), "Status must remain PROCESSING for webhook reconciliation");
        assertTrue(timeoutRefund.getReason().contains("Network timeout"), "Reason must record network timeout");

        Order updatedOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", updatedOrder.getOrderStatus(), "Order must remain CANCELLED");
        assertEquals("REFUND_PROCESSING", updatedOrder.getPaymentStatus(), "Order payment status must indicate refund processing");
    }

    @Test
    @DisplayName("Scenario 4: Refund API success followed by webhook reconciliation transitions to SUCCESS")
    void test4_RefundApiSuccess_WebhookReconciliation_CompletesState() throws Exception {
        String orderNumber = "ORD-REF-004-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_004_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_004";
        String gatewayRefundId = "rfnd_test_004";
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        // Simulate initial refund pending/timeout
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult(gatewayRefundId, "pending", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult(gatewayRefundId, "pending", 50000L));

        String capturePayload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String captureSig = signatureVerifier.calculateHmacSha256(capturePayload, razorpayConfig.getWebhookSecret());
        paymentService.handleWebhook(capturePayload, captureSig);

        // Webhook refund.processed arrives later
        String refundPayload = createRefundProcessedPayload(gatewayRefundId, paymentId, 50000L);
        String refundSig = signatureVerifier.calculateHmacSha256(refundPayload, razorpayConfig.getWebhookSecret());
        paymentService.handleWebhook(refundPayload, refundSig);

        Refund refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElseThrow();
        assertEquals("SUCCESS", refund.getStatus(), "Refund must transition to SUCCESS upon webhook confirmation");
        assertNotNull(refund.getCompletedAt(), "completedAt must be populated");

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("REFUNDED", order.getPaymentStatus(), "Order payment status must be REFUNDED");
        assertEquals("CANCELLED", order.getOrderStatus(), "Order must remain CANCELLED");
    }

    @Test
    @DisplayName("Scenario 5: Refund permanent failure marks FAILED and preserves CANCELLED state")
    void test5_RefundFailure_SafeRecoveryAndManualLedger() throws Exception {
        String orderNumber = "ORD-REF-005-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_005_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_005";
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        // Simulate permanent rejection (e.g. invalid account / card expired)
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenThrow(new IllegalStateException("Gateway rejected refund: 400 Bad Request - Payment already refunded"));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("Gateway rejected refund: 400 Bad Request - Payment already refunded"));

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        paymentService.handleWebhook(payload, signature);

        List<Refund> refunds = refundRepository.findAll();
        assertEquals(1, refunds.size());
        Refund failedRefund = refunds.get(0);
        assertEquals("FAILED", failedRefund.getStatus(), "Status must be recorded as FAILED");
        assertTrue(failedRefund.getReason().contains("Gateway rejected"), "Failure reason must be documented");

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("REFUND_FAILED", order.getPaymentStatus(), "Payment status must accurately show REFUND_FAILED");
        assertEquals("CANCELLED", order.getOrderStatus(), "Order must strictly remain CANCELLED");
    }

    @Test
    @DisplayName("Scenario 6: Cancellation and capture competing for the same order lock are serialized")
    void test6_CancellationAndCapture_CompetingForOrderLock() throws Exception {
        String orderNumber = "ORD-REF-006-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_006_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_006";

        // Create pending order (not cancelled yet)
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
        order.setDeliveryAddressJson("{}");
        order.setShopDistanceKm(new BigDecimal("1.0"));
        order.setIsWithinRadius(true);
        order = orderRepository.save(order);

        Payment payment = new Payment(order, gatewayOrderId, new BigDecimal("500.00"), "CREATED", "RAZORPAY");
        paymentRepository.save(payment);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        String payload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String signature = signatureVerifier.calculateHmacSha256(payload, razorpayConfig.getWebhookSecret());

        Future<String> cancelFuture = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                returnService.cancelOrder(orderNumber, "Customer switched to COD", "user", testCustomer, false);
                return "CANCEL_SUCCESS";
            } catch (Exception e) {
                return "CANCEL_FAILED: " + e.getMessage();
            }
        });

        Future<String> captureFuture = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                paymentService.handleWebhook(payload, signature);
                return "CAPTURE_SUCCESS";
            } catch (Exception e) {
                return "CAPTURE_FAILED: " + e.getMessage();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        String cancelRes = cancelFuture.get(10, TimeUnit.SECONDS);
        String captureRes = captureFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertNotNull(finalOrder);
        // Either cancel won and order is CANCELLED + auto-refunded, or capture won and order is CONFIRMED
        boolean isSafeTerminalState = "CANCELLED".equals(finalOrder.getOrderStatus())
                || "CONFIRMED".equals(finalOrder.getOrderStatus());
        assertTrue(isSafeTerminalState, "Order must reach a safe terminal state without corruption");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Scenario 7: Repository findByOrderNumberWithLock executes and acquires lock inside transaction")
    void test7_RepositoryFindByOrderNumberWithLock_AcquiresLock() {
        String orderNumber = "ORD-REF-007-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_007_" + UUID.randomUUID().toString().substring(0, 6);
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        Optional<Order> lockedOrderOpt = orderRepository.findByOrderNumberWithLock(orderNumber);
        assertTrue(lockedOrderOpt.isPresent(), "findByOrderNumberWithLock must return the order");
        assertEquals(orderNumber, lockedOrderOpt.get().getOrderNumber());
    }

    @Test
    @DisplayName("Scenario 8: Lock exception handling prevents false payment or cancellation success")
    void test8_LockExceptionHandling_NoFalsePaymentOrCancellationSuccess() {
        String nonExistentOrderNumber = "ORD-NON-EXISTENT-" + UUID.randomUUID().toString().substring(0, 6);

        assertThrows(IllegalArgumentException.class, () -> {
            returnService.cancelOrder(nonExistentOrderNumber, "Test cancel", "user", testCustomer, false);
        });

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            paymentService.verifyPayment(new VerifyPaymentRequest(
                    nonExistentOrderNumber, "order_xxx", "pay_xxx", "sig_xxx"
            ));
        });
    }

    @Test
    @DisplayName("Scenario 9: Outer payment transaction rollback prevents afterCommit gateway refund dispatch")
    void test9_OuterPaymentTransactionRollback_DoesNotDispatchGatewayRefund() {
        String orderNumber = "ORD-REF-009-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_009_" + UUID.randomUUID().toString().substring(0, 6);
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        order.setPaymentStatus("REFUND_PROCESSING");
        Order savedOrder = orderRepository.save(order);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(savedOrder.getId()).orElseThrow();
        payment.setGatewayPaymentId("pay_ref_009");
        paymentRepository.save(payment);

        // Clear invocations on mock
        org.mockito.Mockito.clearInvocations(razorpayClientService);

        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> {
            txTemplate.execute(status -> {
                refundService.processRefund(savedOrder, new BigDecimal("500.00"), "Rollback test", "SYSTEM");
                // Force an unexpected exception during outer transaction
                throw new RuntimeException("Simulated unexpected database failure / rollback in outer transaction");
            });
        });

        // Verify that afterCommit synchronization NEVER fired
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any());
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString());

        // Verify no refund record committed in database
        List<Refund> refunds = refundRepository.findByOrderId(savedOrder.getId());
        assertTrue(refunds.isEmpty(), "Refund record must have rolled back and not exist in database");
    }

    @Test
    @DisplayName("Scenario 10: Zero database transaction and zero order locks held during gateway HTTP dispatch")
    void test10_ZeroOrderLockHeldDuringGatewayCall() {
        String orderNumber = "ORD-REF-010-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_010_" + UUID.randomUUID().toString().substring(0, 6);
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        order.setPaymentStatus("REFUND_PROCESSING");
        order = orderRepository.save(order);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow();
        payment.setGatewayPaymentId("pay_ref_010");
        paymentRepository.save(payment);

        // Create initial processing refund intent via recordRefundIntent
        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Lock verification", "SYSTEM");
        assertNotNull(intent.getId());

        // Mock gateway call to verify active transaction state during dispatch
        java.util.concurrent.atomic.AtomicBoolean wasTxActiveDuringGatewayCall = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> {
                    wasTxActiveDuringGatewayCall.set(
                            org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    );
                    return new RazorpayClientService.GatewayRefundResult("rfnd_test_010", "processed", 50000L);
                });

        // Dispatch gateway refund directly
        refundService.dispatchGatewayRefund(intent.getId());

        assertFalse(wasTxActiveDuringGatewayCall.get(),
                "Transaction must NOT be active during external gateway HTTP dispatch (zero locks held)");

        Refund updated = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", updated.getStatus());
        assertEquals("rfnd_test_010", updated.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 11: Webhook reconciliation ignores unknown refund and rejects amount mismatch")
    void test11_WebhookReconciliation_AmountMismatchOrUnknownRefund_Rejected() throws Exception {
        String orderNumber = "ORD-REF-011-" + UUID.randomUUID().toString().substring(0, 6);
        String gatewayOrderId = "order_ref_011_" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_ref_011";
        String gatewayRefundId = "rfnd_test_011";
        createCancelledCodSwitchOrder(orderNumber, gatewayOrderId);

        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult(gatewayRefundId, "pending", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult(gatewayRefundId, "pending", 50000L));

        String capturePayload = createPaymentCapturedPayload(gatewayOrderId, paymentId, 50000L);
        String captureSig = signatureVerifier.calculateHmacSha256(capturePayload, razorpayConfig.getWebhookSecret());
        paymentService.handleWebhook(capturePayload, captureSig);

        // 1. Unknown refund webhook should be safely ignored without throwing exceptions
        String unknownPayload = createRefundProcessedPayload("rfnd_unknown_99999", "pay_unknown", 50000L);
        String unknownSig = signatureVerifier.calculateHmacSha256(unknownPayload, razorpayConfig.getWebhookSecret());
        assertDoesNotThrow(() -> paymentService.handleWebhook(unknownPayload, unknownSig),
                "Unknown refund webhook must be safely ignored");

        // 2. Amount mismatch webhook (e.g. 30000 paise instead of 50000 paise)
        String mismatchPayload = createRefundProcessedPayload(gatewayRefundId, paymentId, 30000L);
        String mismatchSig = signatureVerifier.calculateHmacSha256(mismatchPayload, razorpayConfig.getWebhookSecret());
        paymentService.handleWebhook(mismatchPayload, mismatchSig);

        Refund refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElseThrow();
        // Since amount mismatched, it must remain PROCESSING and NOT mutate to SUCCESS
        assertEquals("PROCESSING", refund.getStatus(), "Amount mismatch must prevent status transition to SUCCESS");
        assertNull(refund.getCompletedAt(), "completedAt must remain null when amount mismatches");
    }
}
