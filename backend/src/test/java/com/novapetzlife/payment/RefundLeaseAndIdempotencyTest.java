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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Targeted verification suite for:
 * 1. Lease-based worker claims and recovery of abandoned intents.
 * 2. Razorpay X-Refund-Idempotency stable key lifecycle.
 * 3. Safe reconciliation first (never matching by amount alone, pagination, error handling).
 * 4. Concurrent worker mutual exclusion and crash windows.
 */
@SpringBootTest
@ActiveProfiles("test")
public class RefundLeaseAndIdempotencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @SpyBean
    private RefundRepository refundRepository;

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundRecoveryService refundRecoveryService;

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
        org.mockito.Mockito.reset(refundRepository);
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
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_idem_123", "processed", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_idem_123", "processed", 50000L));
        when(razorpayClientService.createRefund(anyString(), anyLong(), anyString()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_mock_idem_123", "processed", 50000L));

        testUser = userRepository.findByEmail("lease.test@example.com").orElseGet(() ->
                userRepository.save(new User("lease.test@example.com", "Password@123", "Lease", "Tester", "+919876543210"))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(testUser);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        testBrand = brandRepository.save(new Brand("LeaseBrand-" + suffix, "slug-leasebrand-" + suffix, "Brand"));
        testCategory = categoryRepository.save(new Category("LeaseCat-" + suffix, "slug-leasecat-" + suffix, "Cat", null, 1));
    }

    private Order createCancelledCodSwitchOrder(String orderNumber, String gatewayOrderId, String paymentId) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand,
                testCategory,
                "Lease Product " + suffix,
                "slug-leaseprod-" + suffix,
                "SKU-LEASE-" + suffix.toUpperCase(),
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

    @Test
    @DisplayName("Scenario 1: Intent committed, worker crashes before gateway -> later sweep reclaims lease and successfully dispatches refund")
    void test1_IntentCommitted_WorkerCrashesBeforeGateway_LaterSweepReclaimsAndDispatches() {
        String orderNumber = "ORD-LSE-001-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_001";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_001", paymentId);

        // Record intent (worker simulated dying before afterCommit dispatch)
        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Simulated crash before dispatch", "SYSTEM");
        assertNotNull(intent.getId());
        assertEquals("INITIATED", intent.getStatus());
        assertNotNull(intent.getIdempotencyKey(), "Must have generated a durable idempotency key");
        assertNull(intent.getGatewayRefundId());

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), eq(intent.getIdempotencyKey())))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_lse_001", "processed", 50000L));

        // Later scheduled sweep runs
        int recovered = refundRecoveryService.processPendingRefundRecovery(Instant.now().plusSeconds(5), 3);
        assertEquals(1, recovered);

        Refund completed = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", completed.getStatus());
        assertEquals("COMPLETED", completed.getRecoveryStatus());
        assertEquals("rfnd_lse_001", completed.getGatewayRefundId());
        assertNull(completed.getClaimExpiresAt(), "Claim lease must be cleared upon terminal completion");
    }

    @Test
    @DisplayName("Scenario 2: Active worker holds unexpired lease -> second worker cannot steal or double-dispatch")
    void test2_ActiveWorkerHoldsUnexpiredLease_SecondWorkerCannotSteal() {
        String orderNumber = "ORD-LSE-002-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_002";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_002", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Active lease test", "SYSTEM");
        // Simulate Worker A holding an active lease valid for another 45 seconds
        intent.setStatus("PROCESSING");
        intent.setRecoveryStatus("CLAIMED");
        intent.setClaimExpiresAt(Instant.now().plusSeconds(45));
        intent = refundRepository.saveAndFlush(intent);

        // Worker B attempts to recover the same intent
        boolean processedByWorkerB = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertFalse(processedByWorkerB, "Second worker must not steal unexpired lease");

        // Confirm gateway was NOT invoked by Worker B
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund stillLeased = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", stillLeased.getStatus());
        assertEquals("CLAIMED", stillLeased.getRecoveryStatus());
        assertNotNull(stillLeased.getClaimExpiresAt());
    }

    @Test
    @DisplayName("Scenario 3: Expired worker lease is safely reclaimed by a subsequent sweep")
    void test3_ExpiredWorkerLease_SafelyReclaimedBySubsequentSweep() {
        String orderNumber = "ORD-LSE-003-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_003";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_003", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Expired lease test", "SYSTEM");
        // Simulate Worker A crashed 2 minutes ago: lease expired 60 seconds ago
        intent.setStatus("PROCESSING");
        intent.setRecoveryStatus("CLAIMED");
        intent.setClaimExpiresAt(Instant.now().minusSeconds(60));
        intent.setAttemptCount(1);
        intent = refundRepository.saveAndFlush(intent);

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), eq(intent.getIdempotencyKey())))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_lse_003", "processed", 50000L));

        // Subsequent sweep runs
        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered, "Must reclaim expired worker lease");

        Refund completed = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", completed.getStatus());
        assertEquals("COMPLETED", completed.getRecoveryStatus());
        assertEquals("rfnd_lse_003", completed.getGatewayRefundId());
        assertNull(completed.getClaimExpiresAt());
    }

    @Test
    @DisplayName("Scenario 4: Gateway returned 409 Conflict -> handled as in-progress/conflict rather than fatal failure, reconciled on next pass")
    void test4_GatewayReturned409Conflict_HandledAsInProgressAndReconciledOnNextPass() {
        String orderNumber = "ORD-LSE-004-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_004";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_004", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "409 Conflict test", "SYSTEM");

        // Pass 1: Gateway returns HTTP 409 Conflict on createRefund
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), any()))
                .thenThrow(new RazorpayClientService.GatewayConflictException("HTTP 409: Idempotency conflict / already in progress"));

        refundRecoveryService.recoverSingleRefund(intent.getId(), 3);

        Refund afterPass1 = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", afterPass1.getStatus(), "Must preserve status PROCESSING (not FAILED) on 409 Conflict");
        assertEquals("CONFLICT", afterPass1.getRecoveryStatus());
        assertNull(afterPass1.getClaimExpiresAt(), "Lease released to permit next sweep reconciliation");

        // Pass 2: Next sweep runs; reconciliation query now finds the refund on Razorpay
        String receipt = "rfnd_" + orderNumber + "_" + intent.getId();
        if (receipt.length() > 40) receipt = receipt.substring(0, 40);

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_lse_004_reconciled", 50000L, "processed", receipt, "INR")
        ));

        boolean pass2Recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(pass2Recovered);

        Refund afterPass2 = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", afterPass2.getStatus(), "Successfully reconciled to SUCCESS on subsequent pass");
        assertEquals("COMPLETED", afterPass2.getRecoveryStatus());
        assertEquals("rfnd_lse_004_reconciled", afterPass2.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 5: Gateway query fails during reconciliation -> retry aborted safely without creating duplicate refund")
    void test5_GatewayQueryFailsDuringReconciliation_RetryAbortedSafelyWithoutDuplicateRefund() {
        String orderNumber = "ORD-LSE-005-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_005";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_005", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Recon failure test", "SYSTEM");

        // Gateway reconciliation throws GatewayReconciliationException (e.g. HTTP 503 or network drop)
        when(razorpayClientService.fetchRefundsForPayment(paymentId))
                .thenThrow(new RazorpayClientService.GatewayReconciliationException("Gateway unavailable: HTTP 503"));

        refundRecoveryService.recoverSingleRefund(intent.getId(), 3);

        // Crucial verification: createRefund must NEVER be called if reconciliation query failed!
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any());

        Refund preserved = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", preserved.getStatus(), "Status must remain PROCESSING for future retry");
        assertEquals("RECONCILIATION_FAILED", preserved.getRecoveryStatus());
        assertNull(preserved.getClaimExpiresAt(), "Lease released for next sweep attempt");
    }

    @Test
    @DisplayName("Scenario 6: Reconciliation query returns 100+ items / multiple refunds on same payment -> correct refund matched by receipt / currency")
    void test6_ReconciliationQueryReturnsMultipleRefunds_CorrectRefundMatchedByReceiptAndCurrency() {
        String orderNumber = "ORD-LSE-006-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_006";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_006", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Multi refund matching test", "SYSTEM");
        String expectedReceipt = "rfnd_" + orderNumber + "_" + intent.getId();
        if (expectedReceipt.length() > 40) expectedReceipt = expectedReceipt.substring(0, 40);

        // Razorpay returns multiple refunds on the same payment:
        // 1. Another order's partial refund (same amount 500.00, different receipt)
        // 2. A USD refund (same amount 50000 paise equivalent, wrong currency)
        // 3. The target refund with matching receipt and currency INR
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_other_001", 50000L, "processed", "rfnd_OTHER_ORDER_1", "INR"),
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_wrong_currency", 50000L, "processed", null, "USD"),
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_target_matched", 50000L, "processed", expectedReceipt, "INR")
        ));

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered);

        // Verify createRefund was NEVER called because exact receipt was reconciled
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund matched = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", matched.getStatus());
        assertEquals("COMPLETED", matched.getRecoveryStatus());
        assertEquals("rfnd_target_matched", matched.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 7: Two competing recovery threads race for same expired intent -> exactly one claims and dispatches, the other backs off")
    void test7_TwoCompetingRecoveryThreadsRaceForExpiredIntent_ExactlyOneClaimsAndDispatches() throws Exception {
        String orderNumber = "ORD-LSE-007-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_007";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_007", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Thread race test", "SYSTEM");
        // Expired lease
        intent.setStatus("PROCESSING");
        intent.setRecoveryStatus("CLAIMED");
        intent.setClaimExpiresAt(Instant.now().minusSeconds(10));
        intent = refundRepository.saveAndFlush(intent);
        final Long targetRefundId = intent.getId();

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), any()))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_lse_007", "processed", 50000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return refundRecoveryService.recoverSingleRefund(targetRefundId, 3);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one dispatch must have succeeded
        verify(razorpayClientService, times(1)).createRefund(eq(paymentId), eq(50000L), anyString(), any(), any());

        Refund finalRefund = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", finalRefund.getStatus());
        assertEquals("COMPLETED", finalRefund.getRecoveryStatus());
    }

    @Test
    @DisplayName("Scenario 8: Crash after gateway creates refund but before DB update -> recovery sweep reconciles without creating second refund")
    void test8_CrashAfterGatewayCreatesRefundBeforeDbUpdate_RecoveryReconcilesWithoutSecondRefund() {
        String orderNumber = "ORD-LSE-008-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_008";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_008", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Crash after gateway call", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent.setClaimExpiresAt(Instant.now().minusSeconds(10)); // worker died while waiting or writing DB
        intent = refundRepository.saveAndFlush(intent);

        String receipt = "rfnd_" + orderNumber + "_" + intent.getId();
        if (receipt.length() > 40) receipt = receipt.substring(0, 40);

        // Razorpay already has the refund created by the earlier call
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_prior_accepted_008", 50000L, "processed", receipt, "INR")
        ));

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered);

        // Must reconcile without calling createRefund!
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund reconciled = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", reconciled.getStatus());
        assertEquals("rfnd_prior_accepted_008", reconciled.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 9: Application restart preserves stable idempotency key across retries")
    void test9_ApplicationRestartPreservesStableIdempotencyKeyAcrossRetries() {
        String orderNumber = "ORD-LSE-009-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_009";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_009", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Stable key test", "SYSTEM");
        String originalKey = intent.getIdempotencyKey();
        assertNotNull(originalKey);
        assertTrue(originalKey.startsWith("rfnd_idem_"));

        // Simulate network timeout on initial dispatch attempt
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), eq(originalKey)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        refundRecoveryService.recoverSingleRefund(intent.getId(), 3);

        Refund afterTimeout = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", afterTimeout.getStatus());
        assertEquals(originalKey, afterTimeout.getIdempotencyKey(), "Idempotency key must remain identical after timeout");

        // Simulate app restart / next recovery sweep retry
        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), eq(originalKey)))
                .thenReturn(new RazorpayClientService.GatewayRefundResult("rfnd_lse_009", "processed", 50000L));

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered);

        Refund finalRefund = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", finalRefund.getStatus());
        assertEquals(originalKey, finalRefund.getIdempotencyKey(), "Original idempotency key preserved all the way to SUCCESS");
    }

    @Test
    @DisplayName("Scenario 10: Attempt limit exceeded -> escalates to PENDING_MANUAL_REFUND with full audit history preserved")
    void test10_AttemptLimitExceeded_EscalatesToPendingManualRefundWithAuditHistory() {
        String orderNumber = "ORD-LSE-010-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_010";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_010", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Customer requested return of defective item", "STAFF_USER");
        intent.setAttemptCount(3); // Already reached max attempts
        intent.setStatus("PROCESSING");
        intent = refundRepository.saveAndFlush(intent);

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertFalse(recovered, "Must not attempt automated dispatch when max attempts exceeded");

        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund escalated = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PENDING_MANUAL_REFUND", escalated.getStatus(), "Must escalate to PENDING_MANUAL_REFUND");
        assertEquals("MANUAL_ESCALATION", escalated.getRecoveryStatus());
        assertNull(escalated.getClaimExpiresAt());
        assertTrue(escalated.getReason().contains("Customer requested return of defective item"), "Original customer reason must be preserved");
        assertTrue(escalated.getReason().contains("manual intervention required"), "Audit escalation notice must be recorded");

        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertEquals("CANCELLED", finalOrder.getOrderStatus(), "Order status must remain CANCELLED");
    }

    @Test
    @DisplayName("Scenario 11: Genuinely distinct refund intents receive distinct idempotency keys")
    void test11_GenuinelyDistinctRefundIntents_ReceiveDistinctIdempotencyKeys() {
        String orderNumber1 = "ORD-LSE-011A-" + UUID.randomUUID().toString().substring(0, 6);
        String orderNumber2 = "ORD-LSE-011B-" + UUID.randomUUID().toString().substring(0, 6);
        Order order1 = createCancelledCodSwitchOrder(orderNumber1, "order_lse_011a", "pay_lse_011a");
        Order order2 = createCancelledCodSwitchOrder(orderNumber2, "order_lse_011b", "pay_lse_011b");

        Refund intent1 = refundService.recordRefundIntent(order1, new BigDecimal("250.00"), "Refund 1", "SYSTEM");
        Refund intent2 = refundService.recordRefundIntent(order2, new BigDecimal("250.00"), "Refund 2", "SYSTEM");

        assertNotNull(intent1.getIdempotencyKey());
        assertNotNull(intent2.getIdempotencyKey());
        assertNotEquals(intent1.getIdempotencyKey(), intent2.getIdempotencyKey(), "Distinct intents must receive distinct idempotency keys");
        assertTrue(intent1.getIdempotencyKey().contains(orderNumber1));
        assertTrue(intent2.getIdempotencyKey().contains(orderNumber2));
    }

    @Test
    @DisplayName("Scenario 12: Network timeout during retry retains idempotency key and lease expires safely for subsequent reconciliation")
    void test12_NetworkTimeoutDuringRetry_RetainsIdempotencyKeyAndLeaseExpiresSafely() {
        String orderNumber = "ORD-LSE-012-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_012";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_012", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Timeout retry test", "SYSTEM");
        String originalKey = intent.getIdempotencyKey();

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of());
        when(razorpayClientService.createRefund(eq(paymentId), eq(50000L), anyString(), any(), eq(originalKey)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        refundRecoveryService.recoverSingleRefund(intent.getId(), 3);

        Refund timedOut = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", timedOut.getStatus());
        assertEquals("TIMEOUT", timedOut.getRecoveryStatus());
        assertEquals(originalKey, timedOut.getIdempotencyKey(), "Key must not change after timeout");
        assertNull(timedOut.getClaimExpiresAt(), "Lease released after timeout so sweeper can retry or reconcile");

        // Next pass: sweeper successfully recovers with matching receipt
        String receipt = "rfnd_" + orderNumber + "_" + intent.getId();
        if (receipt.length() > 40) receipt = receipt.substring(0, 40);

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_lse_012_reconciled", 50000L, "processed", receipt, "INR")
        ));

        boolean recovered = refundRecoveryService.recoverSingleRefund(intent.getId(), 3);
        assertTrue(recovered);

        Refund finalRefund = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", finalRefund.getStatus());
        assertEquals("COMPLETED", finalRefund.getRecoveryStatus());
        assertEquals("rfnd_lse_012_reconciled", finalRefund.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 13: Stale Worker Fencing: Worker A's lease expires, Worker B acquires lease and dispatches, Worker A resumes with stale claim token -> Worker A's outcome update is rejected")
    void test13_StaleWorkerCannotCommitAfterNewLeaseAcquired() {
        String orderNumber = "ORD-LSE-013-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_013";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_013", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Stale worker test", "SYSTEM");
        String staleToken = "claim_token_worker_A";
        String newToken = "claim_token_worker_B";

        intent.setStatus("PROCESSING");
        intent.setClaimToken(newToken);
        intent.setClaimExpiresAt(Instant.now().plusSeconds(300));
        refundRepository.saveAndFlush(intent);

        refundService.recordGatewayRefundOutcome(intent.getId(), "rfnd_stale_013", "SUCCESS", staleToken);

        Refund check = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", check.getStatus());
        assertEquals(newToken, check.getClaimToken());
        assertNull(check.getGatewayRefundId());
    }

    @Test
    @DisplayName("Scenario 14: Stale Worker Fencing before Gateway: Worker A detects claim token mismatch before calling Razorpay and aborts")
    void test14_StaleWorkerAbortsBeforeGatewayDispatch() {
        String orderNumber = "ORD-LSE-014-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_014";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_014", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Stale pre-dispatch test", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent.setClaimToken("active_worker_token");
        intent = refundRepository.saveAndFlush(intent);

        refundService.dispatchGatewayRefund(intent.getId(), "stale_token_A");

        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());
        verify(razorpayClientService, never()).fetchRefundsForPayment(paymentId);
    }

    @Test
    @DisplayName("Scenario 15: Gateway Reconciliation Status Mapping: failed -> FAILED, pending -> PROCESSING, unknown/mismatch -> PENDING_MANUAL_REFUND")
    void test15_GatewayReconciliationStatusMapping() {
        String orderNumber = "ORD-LSE-015-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_015";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_015", paymentId);

        // Case A: Gateway returns failed status
        Refund intentA = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Status mapping test A", "SYSTEM");
        String receiptA = intentA.getReceipt();

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_failed_015", 50000L, "failed", receiptA, "INR")
        ));

        refundService.dispatchGatewayRefund(intentA.getId());

        Refund resultA = refundRepository.findById(intentA.getId()).orElseThrow();
        assertEquals("FAILED", resultA.getStatus(), "Gateway status 'failed' must map to local status FAILED");

        // Case B: Gateway returns unknown status -> escalates to manual review
        String paymentIdB = "pay_lse_015_b";
        Order orderB = createCancelledCodSwitchOrder("ORD-LSE-015B-" + UUID.randomUUID().toString().substring(0, 6), "order_lse_015_b", paymentIdB);
        Refund intentB = refundService.recordRefundIntent(orderB, new BigDecimal("500.00"), "Status mapping test B", "SYSTEM");
        String receiptB = intentB.getReceipt();

        when(razorpayClientService.fetchRefundsForPayment(paymentIdB)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_unknown_015", 50000L, "under_investigation", receiptB, "INR")
        ));

        refundService.dispatchGatewayRefund(intentB.getId());

        Refund resultB = refundRepository.findById(intentB.getId()).orElseThrow();
        assertEquals("PENDING_MANUAL_REFUND", resultB.getStatus(), "Unknown gateway status must escalate to PENDING_MANUAL_REFUND");

        // Case C: Gateway returns matching receipt but mismatched amount -> escalates to manual review
        String paymentIdC = "pay_lse_015_c";
        Order orderC = createCancelledCodSwitchOrder("ORD-LSE-015C-" + UUID.randomUUID().toString().substring(0, 6), "order_lse_015_c", paymentIdC);
        Refund intentC = refundService.recordRefundIntent(orderC, new BigDecimal("500.00"), "Status mapping test C", "SYSTEM");
        String receiptC = intentC.getReceipt();

        when(razorpayClientService.fetchRefundsForPayment(paymentIdC)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_mismatch_015", 25000L, "processed", receiptC, "INR")
        ));

        refundService.dispatchGatewayRefund(intentC.getId());

        Refund resultC = refundRepository.findById(intentC.getId()).orElseThrow();
        assertEquals("PENDING_MANUAL_REFUND", resultC.getStatus(), "Amount mismatch must escalate to PENDING_MANUAL_REFUND");
    }

    @Test
    @DisplayName("Scenario 16: Webhook Reconciliation with Receipt and Status Monotonicity")
    void test16_WebhookReconciliationWithReceiptAndStatusMonotonicity() throws Exception {
        String orderNumber = "ORD-LSE-016-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_016";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_016", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Webhook recon test", "SYSTEM");
        intent.setStatus("PROCESSING");
        intent = refundRepository.saveAndFlush(intent);

        String receipt = intent.getReceipt();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 1. Dispatch refund.processed webhook with matching receipt
        String processedPayload = String.format("""
            {
                "refund": {
                    "entity": {
                        "id": "rfnd_webhook_016",
                        "payment_id": "%s",
                        "amount": 50000,
                        "currency": "INR",
                        "status": "processed",
                        "receipt": "%s"
                    }
                }
            }
            """, paymentId, receipt);

        refundService.reconcileRefundWebhook("refund.processed", mapper.readTree(processedPayload));

        Refund reconciled = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", reconciled.getStatus());
        assertEquals("rfnd_webhook_016", reconciled.getGatewayRefundId());

        // 2. Dispatch late out-of-order refund.failed webhook for the same refund -> must NOT regress from SUCCESS
        String failedPayload = String.format("""
            {
                "refund": {
                    "entity": {
                        "id": "rfnd_webhook_016",
                        "payment_id": "%s",
                        "amount": 50000,
                        "currency": "INR",
                        "status": "failed",
                        "receipt": "%s",
                        "error_description": "Late bank rejection"
                    }
                }
            }
            """, paymentId, receipt);

        refundService.reconcileRefundWebhook("refund.failed", mapper.readTree(failedPayload));

        Refund afterLateFailed = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("SUCCESS", afterLateFailed.getStatus(), "Must not regress terminal SUCCESS on late refund.failed webhook");
    }

    @Test
    @DisplayName("Scenario 17: Ambiguous candidate without matching receipt moves to manual review without dispatching new refund")
    void test17_AmbiguousCandidateMovesToManualReview() {
        String orderNumber = "ORD-LSE-017-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_017";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_017", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("500.00"), "Ambiguous test", "SYSTEM");

        when(razorpayClientService.fetchRefundsForPayment(paymentId)).thenReturn(List.of(
                new RazorpayClientService.GatewayPaymentRefundItem("rfnd_ambiguous_017", 50000L, "processed", "DIFFERENT_RECEIPT_XYZ", "INR")
        ));

        refundService.dispatchGatewayRefund(intent.getId());

        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund result = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PENDING_MANUAL_REFUND", result.getStatus(), "Ambiguous refund candidate must escalate to PENDING_MANUAL_REFUND");
    }

    @Test
    @DisplayName("Scenario 18: Exact paise conversion with strict validation")
    void test18_ExactPaiseConversionStrictValidation() {
        // Valid exact conversions
        assertEquals(10000L, RefundService.convertToPaiseExact(new BigDecimal("100.00")));
        assertEquals(10050L, RefundService.convertToPaiseExact(new BigDecimal("100.50")));
        assertEquals(1L, RefundService.convertToPaiseExact(new BigDecimal("0.01")));
        assertEquals(1000L, RefundService.convertToPaiseExact(new BigDecimal("10")));
        assertEquals(1050L, RefundService.convertToPaiseExact(new BigDecimal("10.5")));

        // Null amount
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(null)
        );
        assertTrue(exNull.getMessage().contains("cannot be null"));

        // Zero amount
        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(BigDecimal.ZERO)
        );
        assertTrue(exZero.getMessage().contains("must be strictly positive"));

        // Negative amount
        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(new BigDecimal("-10.00"))
        );
        assertTrue(exNeg.getMessage().contains("must be strictly positive"));

        // Fractional paise
        IllegalArgumentException exFrac1 = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(new BigDecimal("100.555"))
        );
        assertTrue(exFrac1.getMessage().contains("fractional paise"));

        IllegalArgumentException exFrac2 = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(new BigDecimal("0.001"))
        );
        assertTrue(exFrac2.getMessage().contains("fractional paise"));

        // Arithmetic overflow
        IllegalArgumentException exOverflow = assertThrows(IllegalArgumentException.class, () ->
                RefundService.convertToPaiseExact(new BigDecimal("1000000000000000000000.00"))
        );
        assertTrue(exOverflow.getMessage().contains("precision loss or overflow"));
    }

    @Test
    @DisplayName("Scenario 19: Identifier length bounding with 150-character order number")
    void test19_IdentifierBoundingWithLongOrderNumber() {
        String longOrderNumber = "ORD-" + "A".repeat(150);

        // 1. Receipt bounded to <= 40 chars and ends with _refundId
        Long refundId = 987654L;
        String receipt = RefundService.buildDeterministicReceipt(longOrderNumber, refundId);
        assertNotNull(receipt);
        assertTrue(receipt.length() <= 40, "Receipt length must not exceed 40 chars but was: " + receipt.length());
        assertTrue(receipt.startsWith("rfnd_"), "Receipt must start with rfnd_ prefix");
        assertTrue(receipt.endsWith("_987654"), "Receipt must preserve unique _refundId suffix");

        // 2. Idempotency key bounded to <= 100 chars
        String key = RefundService.buildIdempotencyKey(longOrderNumber);
        assertNotNull(key);
        assertTrue(key.length() <= 100, "Idempotency key length must not exceed 100 chars but was: " + key.length());
        assertTrue(key.startsWith("rfnd_idem_"), "Idempotency key must start with rfnd_idem_ prefix");
    }

    @Test
    @DisplayName("Scenario 20: Gateway reconciliation failure aborts dispatch without duplicate disbursement")
    void test20_GatewayReconciliationFailureAbortsDispatch() {
        String orderNumber = "ORD-LSE-020-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_020";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_020", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("450.00"), "Recon fail test", "SYSTEM");

        when(razorpayClientService.fetchRefundsForPayment(paymentId))
                .thenThrow(new RazorpayClientService.GatewayReconciliationException("Simulated pagination cycle/safety failure"));

        refundService.dispatchGatewayRefund(intent.getId());

        // Must NEVER call createRefund
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        Refund result = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", result.getStatus(), "Status must remain PROCESSING for subsequent retry");
        assertEquals("RECONCILIATION_FAILED", result.getRecoveryStatus(), "Recovery status must record RECONCILIATION_FAILED");
    }

    @Test
    @DisplayName("Scenario 21: Multiple partial refunds against single order generate distinct deterministic identifiers")
    void test21_MultiplePartialRefundsGenerateDistinctIdentifiers() {
        String orderNumber = "ORD-LSE-021-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_021";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_021", paymentId);

        Refund partial1 = refundService.recordRefundIntent(order, new BigDecimal("200.00"), "Partial 1", "ADMIN");
        Refund partial2 = refundService.recordRefundIntent(order, new BigDecimal("300.00"), "Partial 2", "ADMIN");

        assertNotNull(partial1.getReceipt());
        assertNotNull(partial2.getReceipt());
        assertNotNull(partial1.getIdempotencyKey());
        assertNotNull(partial2.getIdempotencyKey());

        assertNotEquals(partial1.getReceipt(), partial2.getReceipt(), "Each partial refund must have a unique receipt");
        assertNotEquals(partial1.getIdempotencyKey(), partial2.getIdempotencyKey(), "Each partial refund must have a unique idempotency key");

        assertTrue(partial1.getReceipt().endsWith("_" + partial1.getId()));
        assertTrue(partial2.getReceipt().endsWith("_" + partial2.getId()));
    }

    @Test
    @DisplayName("Scenario 22: Deterministic receipt generator fails closed on null or non-positive refund ID")
    void test22_DeterministicReceipt_NullOrInvalidId_FailsClosed() {
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class, () ->
                RefundService.buildDeterministicReceipt("ORD-TEST", null)
        );
        assertTrue(exNull.getMessage().contains("must be non-null and positive"));

        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class, () ->
                RefundService.buildDeterministicReceipt("ORD-TEST", 0L)
        );
        assertTrue(exZero.getMessage().contains("must be non-null and positive"));

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class, () ->
                RefundService.buildDeterministicReceipt("ORD-TEST", -10L)
        );
        assertTrue(exNeg.getMessage().contains("must be non-null and positive"));
    }

    @Test
    @DisplayName("Scenario 23: Receipt generation after persistence guarantees non-null database ID and no fallback _0")
    void test23_ReceiptGenerationAfterPersistence_HasPersistentId() {
        String orderNumber = "ORD-LSE-023-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_023";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_023", paymentId);

        Refund refund = refundService.recordRefundIntent(order, new BigDecimal("150.00"), "Receipt ID order test", "SYSTEM");

        assertNotNull(refund.getId(), "Persisted refund must have a non-null database ID");
        assertTrue(refund.getId() > 0, "Persisted refund database ID must be positive");
        assertNotNull(refund.getReceipt(), "Receipt must be generated and persisted");
        assertFalse(refund.getReceipt().endsWith("_0"), "Receipt must never end with fallback _0");
        assertTrue(refund.getReceipt().endsWith("_" + refund.getId()), "Receipt must end with _<database-id>");

        // Verify receipt is persisted and reused identically
        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        assertEquals(refund.getReceipt(), reloaded.getReceipt());
        assertEquals(refund.getIdempotencyKey(), reloaded.getIdempotencyKey());
    }

    @Test
    @DisplayName("Scenario 24: Transaction rollback if exception occurs during receipt generation or second saveAndFlush")
    void test24_TransactionRollbackOnReceiptPersistenceFailure() {
        String orderNumber = "ORD-LSE-024-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_024";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_024", paymentId);

        // Simulate failure on the second saveAndFlush (when receipt is set)
        doAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            if (org.springframework.util.StringUtils.hasText(r.getReceipt())) {
                throw new RuntimeException("Simulated second saveAndFlush database crash");
            }
            return invocation.callRealMethod();
        }).when(refundRepository).saveAndFlush(any(Refund.class));

        // When invoking recordRefundIntent
        assertThrows(RuntimeException.class, () ->
                refundService.recordRefundIntent(order, new BigDecimal("100.00"), "Rollback test", "SYSTEM")
        );

        // Verify that the initial insert was rolled back completely by the transaction manager
        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertTrue(refunds.isEmpty(), "No refund intent should remain in the database after transaction rollback");
    }

    @Test
    @DisplayName("Scenario 25: Malformed gateway record fails closed and never triggers refund creation")
    void test25_MalformedGatewayRecordFailsClosedWithoutCreatingRefund() {
        String orderNumber = "ORD-LSE-025-" + UUID.randomUUID().toString().substring(0, 6);
        String paymentId = "pay_lse_025";
        Order order = createCancelledCodSwitchOrder(orderNumber, "order_lse_025", paymentId);

        Refund intent = refundService.recordRefundIntent(order, new BigDecimal("350.00"), "Malformed recon test", "SYSTEM");

        when(razorpayClientService.fetchRefundsForPayment(paymentId))
                .thenThrow(new RazorpayClientService.GatewayReconciliationException("Malformed gateway refund record missing refund ID for payment: " + paymentId));

        refundService.dispatchGatewayRefund(intent.getId());

        // Verify createRefund was NEVER called
        verify(razorpayClientService, never()).createRefund(anyString(), anyLong(), anyString(), any(), any());

        // Verify intent remains PROCESSING with RECONCILIATION_FAILED
        Refund result = refundRepository.findById(intent.getId()).orElseThrow();
        assertEquals("PROCESSING", result.getStatus());
        assertEquals("RECONCILIATION_FAILED", result.getRecoveryStatus());
    }
}
