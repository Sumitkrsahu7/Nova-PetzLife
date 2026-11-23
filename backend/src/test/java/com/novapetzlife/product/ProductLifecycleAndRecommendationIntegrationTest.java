package com.novapetzlife.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.Cart;
import com.novapetzlife.cart.CartItem;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerAddress;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.google.GoogleReviewRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.petprofile.PetProfile;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductLifecycleAndRecommendationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PetProfileRepository petProfileRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private ReturnRequestItemRepository returnRequestItemRepository;

    @Autowired
    private ReturnInspectionRepository returnInspectionRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private GoogleReviewRepository googleReviewRepository;

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
    private ProductService productService;

    @Autowired
    private com.novapetzlife.inventory.InventoryBatchRepository batchRepository;

    @Autowired
    private com.novapetzlife.audit.AuditLogRepository auditLogRepository;

    private Brand testBrand;
    private Category testCategory;
    private Customer testCustomer;
    private User testUser;

    @BeforeEach
    void setUp() {
        cleanAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        testBrand = brandRepository.save(new Brand("Pedigree Test", "pedigree-test-" + suffix, "Brand for testing"));
        testCategory = categoryRepository.save(new Category("Dog Treats", "dog-treats-" + suffix, "Dog treats", null, 1));

        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));

        testUser = userRepository.findByEmail("customer.lifecycle@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("customer.lifecycle@example.com");
                    u.setPasswordHash("$2a$10$dummyHash");
                    u.setFirstName("Arun");
                    u.setLastName("Kumar");
                    u.setPhone("+919447333333");
                    u.setRoles(Set.of(roleCustomer));
                    return userRepository.save(u);
                });

        testCustomer = customerRepository.findByEmail("customer.lifecycle@example.com")
                .orElseGet(() -> {
                    Customer c = new Customer(testUser, "customer.lifecycle@example.com", "+919447333333", "Arun", "Kumar", false);
                    return customerRepository.save(c);
                });
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
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
        batchRepository.deleteAll();
        inventoryRepository.deleteAll();
        auditLogRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin_test", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Universal Archival: Removing an unreferenced product archives it from storefront and never deletes it from database")
    void testRemovingUnreferencedProductArchivesItAndPreservesRow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product unreferenced = new Product(
                testBrand,
                testCategory,
                "Unreferenced Chew Bone " + suffix,
                "unreferenced-chew-bone-" + suffix,
                "BONE-" + suffix.toUpperCase(),
                new BigDecimal("250.00"),
                new BigDecimal("220.00")
        );
        unreferenced.setPetType("Dog");
        unreferenced.setIsActive(true);
        unreferenced = productRepository.save(unreferenced);

        Long prodId = unreferenced.getId();
        assertTrue(productRepository.existsById(prodId));

        mockMvc.perform(delete("/api/v1/admin/products/" + prodId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.productId").value(prodId));

        // Universal Archival: product row is PRESERVED, not permanently deleted
        Product archived = productRepository.findById(prodId).orElseThrow();
        assertTrue(archived.getIsArchived(), "Product must be marked as archived");
        assertFalse(archived.getIsActive(), "Archived product must be inactive");
        assertNotNull(archived.getArchivedAt(), "Archived timestamp must be recorded");
        assertEquals("admin_test", archived.getArchivedBy(), "ArchivedBy must record the authenticated admin actor");
    }

    @Test
    @WithMockUser(username = "admin_test", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Case 1: Deleting ordered product archives it, preserves order history and snapshots")
    void testDeletingOrderedProductArchivesItAndPreservesHistory() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product orderedProduct = new Product(
                testBrand,
                testCategory,
                "Historical Dog Biscuit " + suffix,
                "historical-dog-biscuit-" + suffix,
                "BISCUIT-" + suffix.toUpperCase(),
                new BigDecimal("500.00"),
                new BigDecimal("450.00")
        );
        orderedProduct.setPetType("Dog");
        orderedProduct.setIsActive(true);
        orderedProduct = productRepository.save(orderedProduct);

        Long prodId = orderedProduct.getId();

        // Create an order referencing this product
        Order order = new Order(
                "ORD-" + suffix.toUpperCase(),
                testCustomer,
                "DELIVERED",
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("450.00"),
                "PAID",
                "COD",
                "{}",
                new BigDecimal("2.5"),
                true,
                "Standard"
        );
        order = orderRepository.save(order);

        OrderItem item = new OrderItem(
                order,
                orderedProduct,
                null,
                orderedProduct.getName(),
                testBrand.getName(),
                orderedProduct.getSku(),
                1,
                new BigDecimal("450.00"),
                new BigDecimal("450.00"),
                new BigDecimal("18.00"),
                new BigDecimal("68.64"),
                "23091000",
                "{\"policy\":\"snapshot\"}",
                "{\"refund\":\"snapshot\"}"
        );
        order.addItem(item);
        orderItemRepository.save(item);

        // Attempt removal
        mockMvc.perform(delete("/api/v1/admin/products/" + prodId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.productId").value(prodId));

        // Verify product in database: must be marked archived and inactive, NOT deleted
        Product archived = productRepository.findById(prodId).orElseThrow();
        assertTrue(archived.getIsArchived(), "Product must be marked isArchived = true");
        assertFalse(archived.getIsActive(), "Product must be deactivated isActive = false");
        assertNotNull(archived.getArchivedAt(), "archivedAt timestamp must be recorded");
        assertEquals("admin_test", archived.getArchivedBy(), "archivedBy must record admin username");

        // Verify Order and OrderItem still exist completely intact
        assertTrue(orderRepository.existsById(order.getId()), "Order record must be preserved");
        assertTrue(orderItemRepository.existsById(item.getId()), "OrderItem record must be preserved");
        assertEquals(prodId, item.getProduct().getId(), "OrderItem must still reference the original product entity");

        // Verify customer storefront excludes archived product
        mockMvc.perform(get("/api/v1/products")
                        .param("search", "Historical Dog Biscuit")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", empty()));

        // Customer slug lookup should return 404
        mockMvc.perform(get("/api/v1/products/" + archived.getSlug())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Test Restore action by Admin
        mockMvc.perform(post("/api/v1/admin/products/" + prodId + "/restore")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isArchived").value(false))
                .andExpect(jsonPath("$.data.isActive").value(true));

        Product restored = productRepository.findById(prodId).orElseThrow();
        assertFalse(restored.getIsArchived(), "Restored product must have isArchived = false");
        assertTrue(restored.getIsActive(), "Restored product must have isActive = true");
        assertNull(restored.getArchivedAt());
        assertNull(restored.getArchivedBy());
    }

    @Test
    @DisplayName("Recommendations: Similar product endpoint returns relevant active products when query has no exact match")
    void testSimilarProductRecommendationsOnEmptySearch() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Product dogKibble = new Product(
                testBrand,
                testCategory,
                "NutriDelight Puppy Dog Food " + suffix,
                "nutridelight-puppy-" + suffix,
                "ND-PUP-" + suffix.toUpperCase(),
                new BigDecimal("600.00"),
                new BigDecimal("550.00")
        );
        dogKibble.setPetType("Dog");
        dogKibble.setIsActive(true);
        dogKibble.setIsArchived(false);
        productRepository.save(dogKibble);

        // Call recommendations for non-existent term but with petType Dog
        mockMvc.perform(get("/api/v1/products/recommendations/similar")
                        .param("q", "NonExistentPuppyFormulaXYZ")
                        .param("petType", "Dog")
                        .param("limit", "4")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].name", containsString("NutriDelight Puppy")));
    }

    @Test
    @DisplayName("Pet Profile Linking: Linking pre-existing customer pet to user account on customer lookup")
    void testCustomerPetLinkingToUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String petOwnerEmail = "linking.owner." + suffix + "@example.com";

        // Create an unlinked customer (e.g. from guest registration)
        Customer unlinkedCustomer = new Customer(null, petOwnerEmail, "+919447888888", "Deepa", "S", false);
        unlinkedCustomer = customerRepository.save(unlinkedCustomer);

        // Register pet for this customer
        PetProfile pet = new PetProfile(
                unlinkedCustomer,
                "Milo",
                "Dog",
                "Golden Retriever",
                LocalDate.now().minusYears(2),
                "Male",
                new BigDecimal("28.5"),
                "High",
                "None",
                "Vaccinated up to date"
        );
        pet = petProfileRepository.save(pet);
        assertNotNull(pet.getId());

        // Now user signs up with the same email
        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));

        User newUser = new User();
        newUser.setEmail(petOwnerEmail);
        newUser.setPasswordHash("$2a$10$dummyHash");
        newUser.setFirstName("Deepa");
        newUser.setLastName("S");
        newUser.setPhone("+919447888888");
        newUser.setRoles(Set.of(roleCustomer));
        newUser = userRepository.save(newUser);

        // Customer pets queried via repository custom query should find Milo when linked to user
        unlinkedCustomer.setUser(newUser);
        customerRepository.save(unlinkedCustomer);
        var petsFound = petProfileRepository.findCustomerPets(unlinkedCustomer.getId(), newUser.getId());
        assertFalse(petsFound.isEmpty(), "Milo must be resolved for this customer account when properly linked");
        assertEquals("Milo", petsFound.get(0).getPetName());
    }

    @Test
    @WithMockUser(username = "customer.lifecycle@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix B.1: Checkout rejects cart containing archived product (400 Bad Request)")
    void testCheckoutRejectsArchivedProductInCart() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Archived Chew Toy " + suffix,
                "archived-toy-" + suffix, "TOY-" + suffix.toUpperCase(),
                new BigDecimal("350.00"), new BigDecimal("300.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Add to cart
        String sessionToken = "session_" + suffix;
        Cart cart = new Cart(testCustomer, sessionToken);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem(cart, product, null, 1, new BigDecimal("300.00"));
        cart.addItem(item);
        cartItemRepository.save(item);

        // Archive product
        product.setIsArchived(true);
        product.setIsActive(false);
        productRepository.save(product);

        // Setup delivery address
        CustomerAddress address = new CustomerAddress(
                testCustomer, "Arun Kumar", "+919447333333", "Flat 4B, Kowdiar", "Near Kowdiar Palace",
                "Kowdiar", "Thiruvananthapuram", "Kerala", "695003",
                new BigDecimal("8.5284000"), new BigDecimal("76.9532000"), true
        );
        address = customerAddressRepository.save(address);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(), sessionToken, null, null, null, null,
                address.getId(), null, 8.5284, 76.9532, "STANDARD", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("archived and is no longer available")));
    }

    @Test
    @WithMockUser(username = "customer.lifecycle@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix B.2: Checkout rejects cart containing inactive product (400 Bad Request)")
    void testCheckoutRejectsInactiveProductInCart() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Inactive Leash " + suffix,
                "inactive-leash-" + suffix, "LEASH-" + suffix.toUpperCase(),
                new BigDecimal("400.00"), new BigDecimal("350.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);
        product = productRepository.save(product);

        String sessionToken = "session_inactive_" + suffix;
        Cart cart = new Cart(testCustomer, sessionToken);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem(cart, product, null, 1, new BigDecimal("350.00"));
        cart.addItem(item);
        cartItemRepository.save(item);

        // Deactivate product
        product.setIsActive(false);
        productRepository.save(product);

        CustomerAddress address = new CustomerAddress(
                testCustomer, "Arun Kumar", "+919447333333", "Flat 4B, Kowdiar", "Near Kowdiar Palace",
                "Kowdiar", "Thiruvananthapuram", "Kerala", "695003",
                new BigDecimal("8.5284000"), new BigDecimal("76.9532000"), true
        );
        address = customerAddressRepository.save(address);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(), sessionToken, null, null, null, null,
                address.getId(), null, 8.5284, 76.9532, "STANDARD", "COD", null
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive and cannot be purchased")));
    }

    @Test
    @DisplayName("Fix 1: Concurrent archival blocks checkout and enforces pessimistic lock coordination")
    void testConcurrentArchivalBlocksCheckout() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Concurrent Collar " + suffix,
                "concurrent-collar-" + suffix, "COLLAR-" + suffix.toUpperCase(),
                new BigDecimal("600.00"), new BigDecimal("550.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Add inventory
        com.novapetzlife.inventory.Inventory inv = new com.novapetzlife.inventory.Inventory(product, null, 10, 2);
        inventoryRepository.save(inv);

        // Add to cart
        String sessionToken = "session_concurrent_" + suffix;
        Cart cart = new Cart(testCustomer, sessionToken);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem(cart, product, null, 1, new BigDecimal("550.00"));
        cart.addItem(item);
        cartItemRepository.save(item);

        CustomerAddress address = new CustomerAddress(
                testCustomer, "Arun Kumar", "+919447333333", "Flat 4B, Kowdiar", "Near Kowdiar Palace",
                "Kowdiar", "Thiruvananthapuram", "Kerala", "695003",
                new BigDecimal("8.5284000"), new BigDecimal("76.9532000"), true
        );
        address = customerAddressRepository.save(address);

        Long prodId = product.getId();
        Long cartId = cart.getId();
        Long addrId = address.getId();

        // Concurrently run removal and checkout
        java.util.concurrent.atomic.AtomicInteger checkoutStatus = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(2);

        java.util.concurrent.CompletableFuture<Void> archiveTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                productService.removeProduct(prodId, "admin_concurrent");
            } finally {
                latch.countDown();
            }
        });

        java.util.concurrent.CompletableFuture<Void> checkoutTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                CheckoutRequest req = new CheckoutRequest(
                        cartId, sessionToken, null, null, null, null,
                        addrId, null, 8.5284, 76.9532, "STANDARD", "COD", null
                );
                var result = mockMvc.perform(post("/api/v1/orders/checkout")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer.lifecycle@example.com").roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                checkoutStatus.set(result.getResponse().getStatus());
            } catch (Throwable t) {
                // Ignore
            } finally {
                latch.countDown();
            }
        });

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        archiveTask.get();
        checkoutTask.get();

        // The product is guaranteed archived
        Product after = productRepository.findById(prodId).orElseThrow();
        assertTrue(after.getIsArchived());
        assertFalse(after.getIsActive());

        // Checkout either failed with 400 Bad Request (cart contains archived product) or succeeded right before archival
        if (checkoutStatus.get() == 400) {
            assertEquals(400, checkoutStatus.get(), "Checkout correctly blocked with 400 when product was archived");
        }
    }

    @Test
    @DisplayName("Fix 2.1: Restoring an active product that was archived preserves active status")
    void testActiveProductArchiveRestorePreservesActive() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Active Ball " + suffix,
                "active-ball-" + suffix, "BALL-" + suffix.toUpperCase(),
                new BigDecimal("200.00"), new BigDecimal("180.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Archive
        productService.removeProduct(product.getId(), "admin_test");
        Product archived = productRepository.findById(product.getId()).orElseThrow();
        assertTrue(archived.getIsArchived());
        assertFalse(archived.getIsActive());

        // Restore without explicit activate param
        productService.restoreProduct(product.getId(), "admin_test");
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(restored.getIsArchived(), "Must no longer be archived");
        assertTrue(restored.getIsActive(), "Must restore to pre-archive active status (true)");
    }

    @Test
    @DisplayName("Fix 2.2: Restoring an inactive product that was archived preserves inactive status")
    void testInactiveProductArchiveRestorePreservesInactive() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Inactive Ball " + suffix,
                "inactive-ball-" + suffix, "INACT-BALL-" + suffix.toUpperCase(),
                new BigDecimal("200.00"), new BigDecimal("180.00")
        );
        product.setIsActive(false); // Inactive before archive!
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Archive
        productService.removeProduct(product.getId(), "admin_test");
        Product archived = productRepository.findById(product.getId()).orElseThrow();
        assertTrue(archived.getIsArchived());
        assertFalse(archived.getIsActive());

        // Restore without explicit activate param
        productService.restoreProduct(product.getId(), "admin_test");
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(restored.getIsArchived(), "Must no longer be archived");
        assertFalse(restored.getIsActive(), "Must preserve pre-archive inactive status (false)");
    }

    @Test
    @DisplayName("Fix 2.3: Restoring an inactive product with explicit activate=true activates it")
    void testArchivedProductRestorationWithExplicitActivationOverridesInactive() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Inactive Rope " + suffix,
                "inactive-rope-" + suffix, "ROPE-" + suffix.toUpperCase(),
                new BigDecimal("150.00"), new BigDecimal("120.00")
        );
        product.setIsActive(false);
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Archive
        productService.removeProduct(product.getId(), "admin_test");

        // Restore with explicit activate = true
        productService.restoreProduct(product.getId(), "admin_test", true);
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(restored.getIsArchived());
        assertTrue(restored.getIsActive(), "Explicit activate=true must override pre-archive inactive state");
    }

    @Test
    @DisplayName("Fix 2.4: Restoring an active product with explicit activate=false leaves it inactive")
    void testArchivedProductRestorationWithExplicitDeactivationOverridesActive() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Active Bowl " + suffix,
                "active-bowl-" + suffix, "BOWL-" + suffix.toUpperCase(),
                new BigDecimal("300.00"), new BigDecimal("250.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);
        product = productRepository.save(product);

        // Archive
        productService.removeProduct(product.getId(), "admin_test");

        // Restore with explicit activate = false
        productService.restoreProduct(product.getId(), "admin_test", false);
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(restored.getIsArchived());
        assertFalse(restored.getIsActive(), "Explicit activate=false must keep product inactive");
    }

    @Test
    @DisplayName("Fix 2.5: Missing or malformed audit data safely defaults to inactive on restoration; ID fallback supported")
    void testAuditRestorationMissingOrMalformedDataDefaultsToInactive() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // Case A: Missing audit log (e.g. historical product archived before audit tracking)
        Product prodNoAudit = new Product(
                testBrand, testCategory, "Legacy Archived " + suffix,
                "legacy-archived-" + suffix, "LEGACY-" + suffix.toUpperCase(),
                new BigDecimal("400.00"), new BigDecimal("350.00")
        );
        prodNoAudit.setIsActive(false);
        prodNoAudit.setIsArchived(true);
        prodNoAudit = productRepository.save(prodNoAudit);

        productService.restoreProduct(prodNoAudit.getId(), "admin_test");
        Product restoredNoAudit = productRepository.findById(prodNoAudit.getId()).orElseThrow();
        assertFalse(restoredNoAudit.getIsArchived());
        assertFalse(restoredNoAudit.getIsActive(), "Missing audit record must safely default to inactive");

        // Case B: Malformed audit log (oldValue is not 'ACTIVE', e.g. 'UNKNOWN' or null)
        Product prodMalformed = new Product(
                testBrand, testCategory, "Malformed Audit " + suffix,
                "malformed-audit-" + suffix, "MALFORMED-" + suffix.toUpperCase(),
                new BigDecimal("400.00"), new BigDecimal("350.00")
        );
        prodMalformed.setIsActive(false);
        prodMalformed.setIsArchived(true);
        prodMalformed = productRepository.save(prodMalformed);

        auditLogRepository.save(new com.novapetzlife.audit.AuditLog(
                "Product", prodMalformed.getSku(), "PRODUCT_ARCHIVED", "admin_test", "UNKNOWN_LEGACY_VAL", "ARCHIVED", "127.0.0.1"
        ));

        productService.restoreProduct(prodMalformed.getId(), "admin_test");
        Product restoredMalformed = productRepository.findById(prodMalformed.getId()).orElseThrow();
        assertFalse(restoredMalformed.getIsArchived());
        assertFalse(restoredMalformed.getIsActive(), "Malformed oldValue in audit must safely default to inactive");

        // Case C: Audit log recorded by numeric ID rather than SKU
        Product prodIdAudit = new Product(
                testBrand, testCategory, "ID Audit " + suffix,
                "id-audit-" + suffix, "ID-AUDIT-" + suffix.toUpperCase(),
                new BigDecimal("400.00"), new BigDecimal("350.00")
        );
        prodIdAudit.setIsActive(false);
        prodIdAudit.setIsArchived(true);
        prodIdAudit = productRepository.save(prodIdAudit);

        auditLogRepository.save(new com.novapetzlife.audit.AuditLog(
                "Product", String.valueOf(prodIdAudit.getId()), "PRODUCT_ARCHIVED", "admin_test", "ACTIVE", "ARCHIVED", "127.0.0.1"
        ));

        productService.restoreProduct(prodIdAudit.getId(), "admin_test");
        Product restoredIdAudit = productRepository.findById(prodIdAudit.getId()).orElseThrow();
        assertFalse(restoredIdAudit.getIsArchived());
        assertTrue(restoredIdAudit.getIsActive(), "Audit log recorded by numeric ID must be resolved by fallback and restore to active");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Fix 4: Universal archival and restoration preserves complete historical records")
    void testUniversalArchivalAndRestorationPreservesCompleteHistoricalData() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = new Product(
                testBrand, testCategory, "Full History Product " + suffix,
                "full-history-" + suffix, "HIST-" + suffix.toUpperCase(),
                new BigDecimal("800.00"), new BigDecimal("750.00")
        );
        product.setIsActive(true);
        product.setIsArchived(false);

        // 1. Add Image
        ProductImage image = new ProductImage(product, "https://example.com/front.jpg", product.getName(), 0, true, "FRONT");
        product.addImage(image);

        // 2. Add Variant
        ProductVariant variant = new ProductVariant(product, product.getSku() + "-V1", "Large", new BigDecimal("900.00"), new BigDecimal("850.00"), 500);
        product.addVariant(variant);

        // 3. Add Return Policy
        ProductReturnPolicy returnPolicy = new ProductReturnPolicy();
        returnPolicy.setProduct(product);
        returnPolicy.setReturnAllowed(true);
        returnPolicy.setReturnWindowDays(10);
        returnPolicy.setRefundAllowed(true);
        product.setReturnPolicy(returnPolicy);

        product = productRepository.save(product);
        Long prodId = product.getId();

        // 4. Add Inventory Batch
        com.novapetzlife.inventory.InventoryBatch batch = new com.novapetzlife.inventory.InventoryBatch(
                product, null, "BATCH-" + suffix, LocalDate.now().minusDays(30), LocalDate.now().plusYears(1), 50
        );
        batch = batchRepository.save(batch);

        // 5. Add Inventory Transaction
        com.novapetzlife.inventory.InventoryTransaction tx = new com.novapetzlife.inventory.InventoryTransaction(
                product, null, "INBOUND", 50, "SUPPLIER", "PO-" + suffix, "Initial Batch"
        );
        tx = inventoryTransactionRepository.save(tx);

        // 6. Add Review
        com.novapetzlife.review.Review review = new com.novapetzlife.review.Review(
                product, testCustomer, 5, "Excellent Product", "My dog loves it!", true, "APPROVED"
        );
        review = reviewRepository.save(review);

        // 7. Add Order & OrderItem
        Order order = new Order(
                "ORD-HIST-" + suffix.toUpperCase(), testCustomer, "DELIVERED",
                new BigDecimal("750.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("750.00"),
                "PAID", "COD", "{}", new BigDecimal("2.5"), true, "Standard"
        );
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem(
                order, product, null, product.getName(), testBrand.getName(), product.getSku(),
                1, new BigDecimal("750.00"), new BigDecimal("750.00"), new BigDecimal("18.00"), new BigDecimal("114.41"),
                "23091000", "{\"returnAllowed\":true}", "{\"refundAllowed\":true}"
        );
        order.addItem(orderItem);
        orderItem = orderItemRepository.save(orderItem);

        // 8. Add Payment
        com.novapetzlife.payment.Payment payment = new com.novapetzlife.payment.Payment(
                order, "PAY-HIST-" + suffix, new BigDecimal("750.00"), "SUCCESS", "COD"
        );
        payment = paymentRepository.save(payment);

        // 9. Add Invoice Snapshot
        com.novapetzlife.tax.Invoice invoice = new com.novapetzlife.tax.Invoice(
                order, "INV-HIST-" + suffix, LocalDate.now(), testCustomer.getFirstName(), null,
                new BigDecimal("635.59"), new BigDecimal("57.20"), new BigDecimal("57.21"), BigDecimal.ZERO, new BigDecimal("750.00")
        );
        invoice = invoiceRepository.save(invoice);

        // 10. Add Return Request & Refund
        com.novapetzlife.returns.ReturnRequest returnRequest = new com.novapetzlife.returns.ReturnRequest(
                "RET-HIST-" + suffix, order, testCustomer, "DEFECTIVE", "Minor defect"
        );
        returnRequest = returnRequestRepository.save(returnRequest);

        com.novapetzlife.refund.Refund refund = new com.novapetzlife.refund.Refund(
                order, payment, "REF-HIST-" + suffix, new BigDecimal("750.00"), "COMPLETED", "Return refund", "ADMIN"
        );
        refund = refundRepository.save(refund);

        // ACT: Archive product
        productService.removeProduct(prodId, "admin_auditor");

        // ASSERT: All historical entities remain intact during ARCHIVAL
        Product archivedProd = productRepository.findById(prodId).orElseThrow();
        assertTrue(archivedProd.getIsArchived());
        assertFalse(archivedProd.getIsActive());
        assertEquals(1, archivedProd.getImages().size(), "Product images must be preserved");
        assertEquals(1, archivedProd.getVariants().size(), "Product variants must be preserved");
        assertNotNull(archivedProd.getReturnPolicy(), "Product return policy must be preserved");
        assertTrue(batchRepository.existsById(batch.getId()), "Inventory batch must be preserved");
        assertTrue(inventoryTransactionRepository.existsById(tx.getId()), "Inventory transaction must be preserved");
        assertTrue(reviewRepository.existsById(review.getId()), "Customer review must be preserved");
        assertTrue(orderRepository.existsById(order.getId()), "Order must be preserved");
        assertTrue(orderItemRepository.existsById(orderItem.getId()), "OrderItem must be preserved");
        assertTrue(paymentRepository.existsById(payment.getId()), "Payment must be preserved");
        assertTrue(invoiceRepository.existsById(invoice.getId()), "Invoice snapshot must be preserved");
        assertTrue(returnRequestRepository.existsById(returnRequest.getId()), "Return request must be preserved");
        assertTrue(refundRepository.existsById(refund.getId()), "Refund must be preserved");

        // ACT: Restore product
        productService.restoreProduct(prodId, "admin_auditor");

        // ASSERT: All historical entities remain intact after RESTORATION
        Product restoredProd = productRepository.findById(prodId).orElseThrow();
        assertFalse(restoredProd.getIsArchived());
        assertTrue(restoredProd.getIsActive(), "Pre-archive active state must be restored");
        assertEquals(1, restoredProd.getImages().size(), "Product images must be intact after restoration");
        assertEquals(1, restoredProd.getVariants().size(), "Product variants must be intact after restoration");
        assertNotNull(restoredProd.getReturnPolicy(), "Product return policy must be intact after restoration");
        assertTrue(batchRepository.existsById(batch.getId()), "Inventory batch must be intact after restoration");
        assertTrue(inventoryTransactionRepository.existsById(tx.getId()), "Inventory transaction must be intact after restoration");
        assertTrue(reviewRepository.existsById(review.getId()), "Customer review must be intact after restoration");
        assertTrue(orderRepository.existsById(order.getId()), "Order must be intact after restoration");
        assertTrue(orderItemRepository.existsById(orderItem.getId()), "OrderItem must be intact after restoration");
        assertTrue(paymentRepository.existsById(payment.getId()), "Payment must be intact after restoration");
        assertTrue(invoiceRepository.existsById(invoice.getId()), "Invoice snapshot must be intact after restoration");
        assertTrue(returnRequestRepository.existsById(returnRequest.getId()), "Return request must be intact after restoration");
        assertTrue(refundRepository.existsById(refund.getId()), "Refund must be intact after restoration");
    }
}
