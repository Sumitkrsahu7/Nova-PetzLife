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
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private com.novapetzlife.user.UserRepository userRepository;

    @Autowired
    private com.novapetzlife.user.RoleRepository roleRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.novapetzlife.customer.CustomerService customerService;

    @Autowired
    private com.novapetzlife.order.OrderService orderService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("Security: Email change requires valid current password")
    void testEmailUpdateRequiresPassword() {
        com.novapetzlife.user.Role role = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_CUSTOMER", "Customer account")));
        String email = "pwd_test_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        com.novapetzlife.user.User user = new com.novapetzlife.user.User(
                email, passwordEncoder.encode("CurrentPass123!"), "Test", "User", "+919447300001"
        );
        user.setRoles(java.util.Set.of(role));
        user = userRepository.save(user);

        final com.novapetzlife.user.User finalUser = user;
        // Incorrect password rejected
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.updateEmail(finalUser, "new_" + email, "WrongPassword!");
        });

        // Correct password succeeds
        var res = customerService.updateEmail(finalUser, "new_" + email, "CurrentPass123!");
        assertNotNull(res);
        assertEquals("new_" + email, res.email());
    }

    @Test
    @DisplayName("Security: Account deletion requires valid current password")
    void testAccountDeletionRequiresPassword() {
        com.novapetzlife.user.Role role = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_CUSTOMER", "Customer account")));
        String email = "del_test_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        com.novapetzlife.user.User user = new com.novapetzlife.user.User(
                email, passwordEncoder.encode("DeletePass123!"), "Del", "User", "+919447300002"
        );
        user.setRoles(java.util.Set.of(role));
        user = userRepository.save(user);

        final com.novapetzlife.user.User finalUser = user;
        // Incorrect password rejected
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.deleteAccount(finalUser, "WrongPassword!");
        });

        // Correct password succeeds
        customerService.deleteAccount(finalUser, "DeletePass123!");
        com.novapetzlife.user.User updated = userRepository.findById(finalUser.getId()).orElseThrow();
        assertFalse(updated.getIsActive());
    }

    @Test
    @DisplayName("Security: Abandoned checkout inventory cleanup cancels expired orders")
    void testAbandonedCheckoutCleanup() {
        Order order = new Order();
        order.setOrderNumber("ORD-ABANDONED-" + UUID.randomUUID().toString().substring(0, 6));
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
        order.setDeliverySlot("Evening");
        order.setShopDistanceKm(BigDecimal.ONE);
        order.setIsWithinRadius(true);
        order = orderRepository.save(order);
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofMinutes(45))),
                order.getId());

        orderService.cleanupAbandonedCheckoutReservations();

        Order cancelled = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertEquals("CANCELLED", cancelled.getOrderStatus());
        assertTrue(cancelled.getCancellationReason().contains("30 minutes"));
    }

    @Test
    @Transactional
    @DisplayName("NPZ-VERIFY-006: Super Admin invariant prevents deactivating sole active Super Admin")
    void testSuperAdminInvariant_SoleSuperAdminCannotBeDeactivated() {
        com.novapetzlife.user.Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_SUPER_ADMIN", "Super Admin role")));

        // Ensure exactly 1 active Super Admin in the system
        List<com.novapetzlife.user.User> existingAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName())))
                .toList();

        for (com.novapetzlife.user.User a : existingAdmins) {
            a.setIsActive(false);
            userRepository.save(a);
        }

        com.novapetzlife.user.User soleAdmin = new com.novapetzlife.user.User(
                "sole.admin@novapetzlife.com", passwordEncoder.encode("AdminPass123!"), "Sole", "Admin", "+919447300099"
        );
        soleAdmin.setRoles(java.util.Set.of(superAdminRole));
        soleAdmin.setIsActive(true);
        soleAdmin = userRepository.save(soleAdmin);

        var controller = new com.novapetzlife.user.AdminStaffController(
                userRepository, roleRepository, null, passwordEncoder, auditService, null
        );

        // Another admin attempts to deactivate the sole active Super Admin
        org.springframework.security.core.Authentication otherAdminAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("other.admin@novapetzlife.com", null);

        final Long targetId = soleAdmin.getId();
        assertThrows(IllegalStateException.class, () -> {
            controller.toggleStaffStatus(targetId, otherAdminAuth);
        });

        com.novapetzlife.user.User reloaded = userRepository.findById(soleAdmin.getId()).orElseThrow();
        assertTrue(reloaded.getIsActive(), "Sole Super Admin must remain active");
    }

    @Test
    @DisplayName("NPZ-VERIFY-006: Super Admin account cannot be deleted via customer deleteAccount endpoint")
    void testSuperAdminCannotDeleteAccountViaCustomerEndpoint() {
        com.novapetzlife.user.Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_SUPER_ADMIN", "Super Admin role")));

        com.novapetzlife.user.User superAdmin = new com.novapetzlife.user.User(
                "super.delete.test@novapetzlife.com", passwordEncoder.encode("Pass@123"), "Super", "Admin", "+919447300088"
        );
        superAdmin.setRoles(java.util.Set.of(superAdminRole));
        superAdmin.setIsActive(true);
        superAdmin = userRepository.save(superAdmin);

        final com.novapetzlife.user.User targetUser = superAdmin;
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.deleteAccount(targetUser, "Pass@123", null);
        }, "Super Administrator accounts must be rejected from customer deleteAccount endpoint");

        com.novapetzlife.user.User reloaded = userRepository.findById(superAdmin.getId()).orElseThrow();
        assertTrue(reloaded.getIsActive(), "Super Admin must remain active");
    }

    @Test
    @DisplayName("NPZ-VERIFY-007: OAuth-only user with null password cannot bypass re-authentication")
    void testOAuthUserWithoutPassword_ReAuthenticationGuardsEnforced() {
        com.novapetzlife.user.Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_CUSTOMER", "Customer account")));

        com.novapetzlife.user.User oauthUser = new com.novapetzlife.user.User();
        oauthUser.setEmail("oauth.customer@example.com");
        oauthUser.setPasswordHash(null); // OAuth user with NO local password
        oauthUser.setFirstName("OAuth");
        oauthUser.setLastName("User");
        oauthUser.setRoles(java.util.Set.of(customerRole));
        oauthUser.setIsActive(true);
        oauthUser = userRepository.save(oauthUser);

        final com.novapetzlife.user.User finalOauthUser = oauthUser;

        // 1. updateEmail without Google ID token rejected
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.updateEmail(finalOauthUser, "hacked@example.com", null);
        });

        // 2. deleteAccount without Google ID token rejected
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.deleteAccount(finalOauthUser, null);
        });

        // Account must remain active and untampered
        com.novapetzlife.user.User reloaded = userRepository.findById(oauthUser.getId()).orElseThrow();
        assertTrue(reloaded.getIsActive());
        assertEquals("oauth.customer@example.com", reloaded.getEmail());
    }
}
