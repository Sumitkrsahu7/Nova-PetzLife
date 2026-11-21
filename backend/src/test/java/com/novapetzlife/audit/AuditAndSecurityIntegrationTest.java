package com.novapetzlife.audit;

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
import com.novapetzlife.google.GoogleReviewRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductImageRepository;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.review.Review;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.dto.ModerateReviewRequest;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.tax.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private GoogleReviewRepository googleReviewRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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

    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        cleanAll();

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
        testCustomer = new Customer(null, "radhika." + suffix + "@example.com", "+9194473" + suffix, "Radhika", "Menon", true);
        testCustomer = customerRepository.save(testCustomer);

        // 3. Category & Brand
        Category category = categoryRepository.save(new Category("Cat Hygiene", "cat-hygiene-" + suffix, "Litter and deodorizers", null, 1));
        Brand brand = brandRepository.save(new Brand("Sanicat", "sanicat-" + suffix, "Mineral Cat Litter"));

        // 4. Product
        testProduct = new Product(
                brand,
                category,
                "Sanicat Clumping Bentonite Cat Litter (10L)",
                "sanicat-clumping-10l-" + suffix,
                "SANI-LIT-" + suffix,
                new BigDecimal("850.00"),
                new BigDecimal("780.00")
        );
        testProduct.setIsActive(true);
        testProduct = productRepository.save(testProduct);
        inventoryService.initializeStock(testProduct, null, 20, 4);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        auditLogRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        googleReviewRepository.deleteAll();
        productImageRepository.deleteAll();
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
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Audit Logging: Order status update automatically writes immutable audit log with previous/new state")
    void testAuditLogCreationOnOrderStatusChange() throws Exception {
        Order order = new Order(
                "ORD-AUDIT-" + UUID.randomUUID().toString().substring(0, 6),
                testCustomer,
                "CONFIRMED",
                new BigDecimal("780.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("780.00"),
                "PENDING",
                "COD",
                "{}",
                new BigDecimal("1.50"),
                true,
                "Morning"
        );
        order = orderRepository.save(order);

        // Update status via Admin endpoint
        mockMvc.perform(patch("/api/v1/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "OUT_FOR_DELIVERY",
                                "reason", "Handed over to Kuravankonam delivery partner"
                        ))))
                .andExpect(status().isOk());

        // Verify Audit Log was recorded
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());

        AuditLog orderAudit = logs.stream()
                .filter(l -> "Order".equals(l.getEntityName()) && "ORDER_STATUS_CHANGED".equals(l.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(orderAudit, "Expected an audit log for ORDER_STATUS_CHANGED");
        assertEquals("CONFIRMED", orderAudit.getOldValue());
        assertEquals("OUT_FOR_DELIVERY", orderAudit.getNewValue());
        assertEquals(order.getOrderNumber(), orderAudit.getEntityId());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Audit Logging: Review moderation writes audit trail recording action and updated review status")
    void testAuditLogCreationOnReviewModeration() throws Exception {
        Review review = new Review(
                testProduct,
                testCustomer,
                5,
                "Great cat litter",
                "Very good clumping and odor control.",
                false,
                "PENDING"
        );
        review = reviewRepository.save(review);

        ModerateReviewRequest modReq = new ModerateReviewRequest("APPROVED", "Thank you Radhika for your feedback!");
        mockMvc.perform(patch("/api/v1/admin/reviews/" + review.getId() + "/moderate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modReq)))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        AuditLog reviewAudit = logs.stream()
                .filter(l -> "Review".equals(l.getEntityName()) && "REVIEW_MODERATED".equals(l.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(reviewAudit, "Expected an audit log for REVIEW_MODERATED");
        assertEquals("APPROVED", reviewAudit.getNewValue());
        assertEquals(String.valueOf(review.getId()), reviewAudit.getEntityId());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Admin Audit Query: Admin can query paginated audit logs with entity and action filters")
    void testAdminAuditLogQueryEndpoint() throws Exception {
        // Manually record sample audit entries
        auditService.recordAudit("Order", "ORD-TEST-100", "ORDER_STATUS_CHANGED", "staff_admin", "CONFIRMED", "SHIPPED", "192.168.1.10");
        auditService.recordAudit("Inventory", "SKU-990", "STOCK_ADJUSTED", "store_owner", "10", "15", "192.168.1.10");

        // Query filtered by entityName=Order
        mockMvc.perform(get("/api/v1/admin/audit-logs?entityName=Order&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].entityId").value("ORD-TEST-100"))
                .andExpect(jsonPath("$.data.content[0].action").value("ORDER_STATUS_CHANGED"));
    }

    @Test
    @DisplayName("Security Hardening: Response contains hardened HTTP security headers (DENY frame, nosniff, HSTS over HTTPS)")
    void testSecurityHeadersPresentInResponse() throws Exception {
        // Over plain HTTP, X-Frame-Options and X-Content-Type-Options are enforced
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Over HTTPS (RFC 6797 compliant), Strict-Transport-Security is additionally emitted
        mockMvc.perform(get("/api/v1/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("Security Enforcement: Unauthenticated requests to /api/v1/admin/audit-logs are rejected")
    void testAuditLogEndpointRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}
