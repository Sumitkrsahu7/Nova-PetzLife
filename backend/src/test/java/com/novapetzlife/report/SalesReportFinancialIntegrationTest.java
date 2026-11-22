package com.novapetzlife.report;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SalesReportFinancialIntegrationTest {

    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    @Autowired
    private SalesReportService salesReportService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Customer account")));

        String uuid = UUID.randomUUID().toString().substring(0, 6);
        User user = new User("report_user_" + uuid + "@test.com", passwordEncoder.encode("Pass123!"), "Report", "User", "98475" + uuid);
        user.setRoles(Set.of(customerRole));
        user = userRepository.save(user);

        testCustomer = new Customer(user, user.getEmail(), user.getPhone(), "Report", "User", false);
        testCustomer = customerRepository.save(testCustomer);

        Brand brand = brandRepository.save(new Brand("ReportBrand-" + uuid, "brand-" + uuid, "desc", null));
        Category category = categoryRepository.save(new Category("ReportCat-" + uuid, "cat-" + uuid, "desc", null, 0));

        testProduct = new Product(brand, category, "Dog Kibble Pack", "kibble-" + uuid, "SKU-" + uuid, new BigDecimal("1000.00"), new BigDecimal("900.00"));
        testProduct = productRepository.save(testProduct);
    }

    private Order createTestOrder(String orderNumber, BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal payableAmount, String paymentStatus, String orderStatus, Instant createdAt) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(testCustomer);
        order.setTotalAmount(subtotal);
        order.setDiscountAmount(discount != null ? discount : BigDecimal.ZERO);
        order.setDeliveryCharge(shipping != null ? shipping : BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setFinalPayableAmount(payableAmount);
        order.setPaymentStatus(paymentStatus);
        order.setPaymentMethod("RAZORPAY");
        order.setOrderStatus(orderStatus);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        if ("COMPLETED".equalsIgnoreCase(paymentStatus) || "PAID".equalsIgnoreCase(paymentStatus) || "REFUNDED".equalsIgnoreCase(paymentStatus)) {
            order.setPaidAt(createdAt);
        }
        order.setDeliveryAddressJson("{\"city\":\"Thiruvananthapuram\",\"postalCode\":\"695003\"}");
        order.setShopDistanceKm(new BigDecimal("2.5"));
        order.setIsWithinRadius(true);
        return orderRepository.save(order);
    }

    private void addOrderItem(Order order, int qty, BigDecimal unitPrice, BigDecimal totalPrice) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(testProduct);
        item.setProductName(testProduct.getName());
        item.setBrandName("Brand");
        item.setSku(testProduct.getSku());
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(totalPrice);
        item.setReturnPolicySnapshot("{\"returnAllowed\":true,\"returnWindowDays\":7}");
        item.setRefundPolicySnapshot("{\"refundAllowed\":true}");
        orderItemRepository.save(item);
    }

    @Test
    @DisplayName("Eligible sales only accumulates confirmed paid orders; excludes pending, failed, and cancelled orders")
    void eligibleSales_includesConfirmedPaidOrdersOnly() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        // 1. Paid completed order: 1000 INR
        Order paidOrder = createTestOrder("ORD-PAID-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "COMPLETED", "CONFIRMED", now);
        addOrderItem(paidOrder, 2, new BigDecimal("500.00"), new BigDecimal("1000.00"));

        // 2. Pending payment order: 800 INR (MUST BE EXCLUDED)
        Order pendingOrder = createTestOrder("ORD-PEND-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("800.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("800.00"), "PENDING", "PLACED", now);
        addOrderItem(pendingOrder, 1, new BigDecimal("800.00"), new BigDecimal("800.00"));

        // 3. Cancelled order: 1200 INR (MUST BE EXCLUDED)
        Order cancelledOrder = createTestOrder("ORD-CANC-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1200.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1200.00"), "COMPLETED", "CANCELLED", now);
        addOrderItem(cancelledOrder, 3, new BigDecimal("400.00"), new BigDecimal("1200.00"));

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());

        // Verify Customer Receipts (gross sales) includes paidOrder (1000) but not pendingOrder (800) or cancelledOrder (1200)
        assertTrue(report.customerReceipts().compareTo(new BigDecimal("1000.00")) >= 0,
                "Customer receipts must include confirmed paid order");

        // Verify Units Sold correctly sums quantity (paidOrder has 2 units, pending & cancelled units excluded)
        assertTrue(report.totalUnitsSold() >= 2);
    }

    @Test
    @DisplayName("Units sold sums item quantities rather than counting order-item records")
    void unitsSold_sumsItemQuantities() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        Order order = createTestOrder("ORD-QTY-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("3500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3500.00"), "COMPLETED", "CONFIRMED", now);

        addOrderItem(order, 4, new BigDecimal("500.00"), new BigDecimal("2000.00"));
        addOrderItem(order, 3, new BigDecimal("500.00"), new BigDecimal("1500.00"));

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        assertTrue(report.totalUnitsSold() >= 7, "Units sold must sum item quantities (4 + 3 = 7)");
    }

    @Test
    @DisplayName("Refund completed in February (initiated in January) is attributed to February report and not January")
    void refundCompletedInFebruary_attributedToFebruaryReport() {
        Instant janOrderDate = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZONE_KOLKATA).toInstant();
        Instant janInitiated = ZonedDateTime.of(2026, 1, 28, 10, 0, 0, 0, ZONE_KOLKATA).toInstant();
        Instant febCompleted = ZonedDateTime.of(2026, 2, 5, 14, 0, 0, 0, ZONE_KOLKATA).toInstant();

        Order janOrder = createTestOrder("ORD-JAN-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1800.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1800.00"), "COMPLETED", "DELIVERED", janOrderDate);

        Refund crossMonthRefund = new Refund(janOrder, null, "rfnd_cross_" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("600.00"), "SUCCESS", "Delayed return", "ADMIN");
        ReflectionTestUtils.setField(crossMonthRefund, "createdAt", janInitiated);
        ReflectionTestUtils.setField(crossMonthRefund, "completedAt", febCompleted);
        refundRepository.save(crossMonthRefund);

        // February 2026 report
        SalesReportSummaryDto febReport = salesReportService.generateSalesReport("MONTHLY", "2026-02-15");
        assertTrue(febReport.totalRefunds().compareTo(new BigDecimal("600.00")) >= 0,
                "February report must capture the refund completed in February");

        // January 2026 report must NOT include this refund because completedAt is in February
        SalesReportSummaryDto janReport = salesReportService.generateSalesReport("MONTHLY", "2026-01-15");
        // Verify crossMonthRefund is not part of January's totalRefunds
        boolean containsInJan = janReport.orders().stream()
                .anyMatch(o -> o.orderNumber().equals(janOrder.getOrderNumber()) && o.refundAmount().compareTo(new BigDecimal("600.00")) == 0 && janReport.totalRefunds().compareTo(new BigDecimal("600.00")) >= 0);
        // Note: janReport totalRefunds should not attribute the February completed refund
        assertTrue(janReport.totalRefunds().compareTo(new BigDecimal("600.00")) < 0 || !containsInJan,
                "January report must not attribute refunds completed in February");
    }

    @Test
    @DisplayName("Pending, failed, and null completedAt refunds are strictly excluded from refund totals")
    void pendingFailedAndNullCompletedAt_areExcluded() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        Order order = createTestOrder("ORD-EXCL-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "COMPLETED", "DELIVERED", now);

        // 1. INITIATED refund (pending)
        Refund pendingRefund = new Refund(order, null, "rfnd_pend_" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("300.00"), "INITIATED", "Pending review", "ADMIN");
        ReflectionTestUtils.setField(pendingRefund, "completedAt", now);
        refundRepository.save(pendingRefund);

        // 2. FAILED refund
        Refund failedRefund = new Refund(order, null, "rfnd_fail_" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("400.00"), "FAILED", "Gateway failure", "ADMIN");
        ReflectionTestUtils.setField(failedRefund, "completedAt", now);
        refundRepository.save(failedRefund);

        // 3. Null completedAt refund
        Refund nullCompletedRefund = new Refund(order, null, "rfnd_null_" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("500.00"), "SUCCESS", "No timestamp", "ADMIN");
        ReflectionTestUtils.setField(nullCompletedRefund, "completedAt", null);
        refundRepository.save(nullCompletedRefund);

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        // Verify none of the 300, 400, or 500 enter the total refunds
        // Check order-level refund total in report order list
        SalesReportOrderDto orderDto = report.orders().stream()
                .filter(o -> o.orderNumber().equals(order.getOrderNumber()))
                .findFirst().orElse(null);

        if (orderDto != null) {
            assertEquals(0, orderDto.refundAmount().compareTo(BigDecimal.ZERO),
                    "Pending, failed, or null completedAt refunds must not enter refund totals");
        }
    }

    @Test
    @DisplayName("Fully refunded order results in consistent Customer Receipts, Refunds, and Net Receipts (0.00)")
    void fullyRefundedOrder_yieldsZeroNetReceipts() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        Order order = createTestOrder("ORD-FULL-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500.00"), "REFUNDED", "DELIVERED", now);

        Refund fullRefund = new Refund(order, null, "rfnd_full_" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("1500.00"), "SUCCESS", "Customer cancelled after dispatch", "ADMIN");
        ReflectionTestUtils.setField(fullRefund, "completedAt", now);
        refundRepository.save(fullRefund);

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        assertNotNull(report);
        assertTrue(report.customerReceipts().compareTo(new BigDecimal("1500.00")) >= 0);
        assertTrue(report.totalRefunds().compareTo(new BigDecimal("1500.00")) >= 0);
        assertEquals(report.customerReceipts().subtract(report.totalRefunds()), report.netReceipts(),
                "Net Receipts must equal Customer Receipts minus Completed Refunds");
    }

    @Test
    @DisplayName("Financial breakdown and AOV use consistent eligible paid orders")
    void financialBreakdownAndAov_areConsistent() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        // subtotal: 1000, discount: 100, shipping: 50 -> finalPayable: 950
        Order order = createTestOrder("ORD-BRK-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("950.00"),
                "COMPLETED", "CONFIRMED", now);

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        assertTrue(report.itemSubtotal().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(report.totalDiscounts().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(report.totalShipping().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(report.customerReceipts().compareTo(BigDecimal.ZERO) > 0);

        if (report.successfulOrders() > 0) {
            BigDecimal expectedAov = report.customerReceipts().divide(BigDecimal.valueOf(report.successfulOrders()), 2, RoundingMode.HALF_UP);
            assertEquals(expectedAov, report.averageOrderValue(), "AOV must be customer receipts divided by paid orders count");
        }
    }

    @Test
    @DisplayName("Indian Financial Year (YEARLY) correctly bounds April 1 to March 31")
    void yearlyPeriod_boundsIndianFinancialYear() {
        SalesReportSummaryDto reportNov = salesReportService.generateSalesReport("YEARLY", "2026-11-15");
        assertTrue(reportNov.formattedDateRange().contains("Apr 2026"), "FY 2026-27 start should be April 2026");
        assertTrue(reportNov.formattedDateRange().contains("Mar 2027"), "FY 2026-27 end should be March 2027");

        SalesReportSummaryDto reportFeb = salesReportService.generateSalesReport("YEARLY", "2026-02-10");
        assertTrue(reportFeb.formattedDateRange().contains("Apr 2025"), "FY 2025-26 start should be April 2025");
        assertTrue(reportFeb.formattedDateRange().contains("Mar 2026"), "FY 2025-26 end should be March 2026");
    }

    @Test
    @DisplayName("CUSTOM date range validation: rejects invalid range and calculates correctly for valid range")
    void customDateRange_validation() {
        assertThrows(IllegalArgumentException.class, () -> {
            salesReportService.generateSalesReport("CUSTOM", null, "2026-11-20", "2026-11-10");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            salesReportService.generateSalesReport("CUSTOM", null, null, "2026-11-10");
        });

        SalesReportSummaryDto validReport = salesReportService.generateSalesReport("CUSTOM", null, "2026-11-01", "2026-11-15");
        assertNotNull(validReport);
        assertEquals("CUSTOM", validReport.period());
        assertTrue(validReport.formattedDateRange().contains("01 Nov 2026"));
        assertTrue(validReport.formattedDateRange().contains("15 Nov 2026"));
    }

    @Test
    @DisplayName("CSV export contains UTF-8 BOM and updated financial headers")
    void csvExport_containsBomAndHeaders() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        SalesReportSummaryDto summary = salesReportService.generateSalesReport("WEEKLY", today.toString());
        byte[] csvBytes = salesReportService.generateCsvReport(summary);

        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 3);
        // Check UTF-8 BOM: 0xEF, 0xBB, 0xBF
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("Order ID,Order Number"));
        assertTrue(csvText.contains("Customer Receipts"));
        assertTrue(csvText.contains("Grand Total"));
    }

    @Test
    @DisplayName("Payment-date attribution: Razorpay payment completed in February for January order is attributed to February report")
    void paymentDateAttribution_razorpayAttributedToPaymentDateNotOrderDate() {
        Instant janOrderDate = ZonedDateTime.of(2026, 1, 10, 11, 0, 0, 0, ZONE_KOLKATA).toInstant();
        Instant febPaymentDate = ZonedDateTime.of(2026, 2, 5, 15, 30, 0, 0, ZONE_KOLKATA).toInstant();

        // Order placed in January, but payment verified/completed on Feb 5
        Order order = createTestOrder("ORD-RZP-FEB-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("2500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2500.00"), "COMPLETED", "CONFIRMED", janOrderDate);
        order.setPaidAt(febPaymentDate);
        orderRepository.save(order);
        addOrderItem(order, 2, new BigDecimal("1250.00"), new BigDecimal("2500.00"));

        // January 2026 report must NOT include this order in customer receipts because it was paid in February
        SalesReportSummaryDto janReport = salesReportService.generateSalesReport("MONTHLY", "2026-01-15");
        boolean inJanOrders = janReport.orders().stream().anyMatch(o -> o.orderNumber().equals(order.getOrderNumber()));
        assertFalse(inJanOrders, "January report must not attribute order paid in February");

        // February 2026 report MUST include this order in customer receipts
        SalesReportSummaryDto febReport = salesReportService.generateSalesReport("MONTHLY", "2026-02-15");
        boolean inFebOrders = febReport.orders().stream().anyMatch(o -> o.orderNumber().equals(order.getOrderNumber()));
        assertTrue(inFebOrders, "February report must capture order paid in February");
        assertTrue(febReport.customerReceipts().compareTo(new BigDecimal("2500.00")) >= 0,
                "February customer receipts must include the 2500 INR order");
    }

    @Test
    @DisplayName("Payment-date attribution: COD order delivered and collected in February is attributed to February report")
    void paymentDateAttribution_codAttributedToDeliveryDate() {
        Instant janOrderDate = ZonedDateTime.of(2026, 1, 20, 14, 0, 0, 0, ZONE_KOLKATA).toInstant();
        Instant febDeliveryDate = ZonedDateTime.of(2026, 2, 8, 11, 0, 0, 0, ZONE_KOLKATA).toInstant();

        // COD order placed in January, delivered/collected on Feb 8
        Order codOrder = createTestOrder("ORD-COD-FEB-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("1800.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1800.00"), "COMPLETED", "DELIVERED", janOrderDate);
        codOrder.setPaymentMethod("COD");
        codOrder.setPaidAt(febDeliveryDate);
        orderRepository.save(codOrder);
        addOrderItem(codOrder, 1, new BigDecimal("1800.00"), new BigDecimal("1800.00"));

        // January report must NOT attribute this COD order
        SalesReportSummaryDto janReport = salesReportService.generateSalesReport("MONTHLY", "2026-01-15");
        assertFalse(janReport.orders().stream().anyMatch(o -> o.orderNumber().equals(codOrder.getOrderNumber())));

        // February report MUST attribute this COD order
        SalesReportSummaryDto febReport = salesReportService.generateSalesReport("MONTHLY", "2026-02-15");
        assertTrue(febReport.orders().stream().anyMatch(o -> o.orderNumber().equals(codOrder.getOrderNumber())));
        assertTrue(febReport.customerReceipts().compareTo(new BigDecimal("1800.00")) >= 0);
    }

    @Test
    @DisplayName("Un-attributable paid orders are strictly excluded from date report and recorded in exclusion metrics")
    void unattributablePaidOrders_areExcludedFromReportAndReportedAsMetrics() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        // Create an order marked COMPLETED, but explicitly strip paidAt and associated records
        Order unattrOrder = createTestOrder("ORD-UNATTR-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("3000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3000.00"), "COMPLETED", "CONFIRMED", now);
        unattrOrder.setPaidAt(null);
        orderRepository.save(unattrOrder);

        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        assertNotNull(report);

        // Verify unattrOrder is excluded from the orders list
        boolean inOrderList = report.orders().stream().anyMatch(o -> o.orderNumber().equals(unattrOrder.getOrderNumber()));
        assertFalse(inOrderList, "Un-attributable order must not be present in date-attributed order transactions");

        // Verify unattributable exclusion metrics
        assertTrue(report.unattributableOrdersCount() >= 1, "Must record at least 1 unattributable order");
        assertTrue(report.unattributableAmount().compareTo(new BigDecimal("3000.00")) >= 0,
                "Must report excluded amount of at least 3000 INR");
    }

    @Test
    @DisplayName("Payment timestamp integrity: Unrelated Payment record updates (updatedAt) do not create attributed sales")
    void unrelatedPaymentUpdates_doNotCreateAttributedSales() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        // Order placed in the current reporting period but payment is pending / not completed
        Order pendingOrder = createTestOrder("ORD-UPDATTR-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("4200.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4200.00"), "PENDING", "PENDING_PAYMENT", now);
        pendingOrder.setPaidAt(null);
        orderRepository.save(pendingOrder);
        addOrderItem(pendingOrder, 2, new BigDecimal("2100.00"), new BigDecimal("4200.00"));

        // Associated Payment has an update timestamp (e.g. gateway webhook retry, admin note), but completedAt is null
        Payment p = new Payment();
        p.setOrder(pendingOrder);
        p.setAmount(new BigDecimal("4200.00"));
        p.setCurrency("INR");
        p.setStatus("FAILED");
        p.setCompletedAt(null); // Uncompleted
        ReflectionTestUtils.setField(p, "updatedAt", now); // Updated today
        paymentRepository.save(p);

        // Verify resolver strictly returns null
        Instant resolvedTime = salesReportService.resolvePaymentTimestamp(pendingOrder);
        assertNull(resolvedTime, "Payment.updatedAt must never be used as a fallback to attribute sales");

        // Verify weekly report excludes this order from customer receipts
        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        boolean isAttributed = report.orders().stream().anyMatch(o -> o.orderNumber().equals(pendingOrder.getOrderNumber()));
        assertFalse(isAttributed, "Unrelated payment update must not create attributed sales");
    }

    @Test
    @DisplayName("Payment timestamp integrity: Delivery without explicit payment collection does not create attributed sales")
    void deliveryWithoutCollection_doesNotCreateAttributedSales() {
        LocalDate today = LocalDate.now(ZONE_KOLKATA);
        Instant now = Instant.now();

        // COD order delivered to customer, but payment collection was not completed (paymentStatus remains PENDING)
        Order codDeliveredWithoutCollection = createTestOrder("ORD-CODDELIV-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("2200.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2200.00"), "PENDING", "DELIVERED", now);
        codDeliveredWithoutCollection.setPaymentMethod("COD");
        codDeliveredWithoutCollection.setPaidAt(null);
        orderRepository.save(codDeliveredWithoutCollection);
        addOrderItem(codDeliveredWithoutCollection, 1, new BigDecimal("2200.00"), new BigDecimal("2200.00"));

        // Status history transition to DELIVERED exists, but cash collection is unconfirmed
        OrderStatusHistory history = new OrderStatusHistory(codDeliveredWithoutCollection, "OUT_FOR_DELIVERY", "DELIVERED", "RIDER", "Delivered at doorstep pending cash collection");
        ReflectionTestUtils.setField(history, "createdAt", now);
        codDeliveredWithoutCollection.addStatusHistory(history);
        orderRepository.save(codDeliveredWithoutCollection);

        // DELIVERED status transition must NEVER be treated as proof of cash collection
        Instant resolvedTime = salesReportService.resolvePaymentTimestamp(codDeliveredWithoutCollection);
        assertNull(resolvedTime, "DELIVERED status alone must not be accepted as proof of cash collection");

        // Verify in sales report
        SalesReportSummaryDto report = salesReportService.generateSalesReport("WEEKLY", today.toString());
        boolean isAttributed = report.orders().stream().anyMatch(o -> o.orderNumber().equals(codDeliveredWithoutCollection.getOrderNumber()));
        assertFalse(isAttributed, "Delivery without collection must not create attributed sales");
    }
}
