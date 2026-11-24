package com.novapetzlife.review;

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
import com.novapetzlife.google.GoogleReview;
import com.novapetzlife.google.GoogleReviewRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.media.dto.AddProductImageRequest;
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
import com.novapetzlife.review.dto.CreateReviewRequest;
import com.novapetzlife.review.dto.ModerateReviewRequest;
import com.novapetzlife.review.dto.ReviewDto;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewAndGoogleBusinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private com.novapetzlife.user.UserRepository userRepository;

    @Autowired
    private com.novapetzlife.user.RoleRepository roleRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
        com.novapetzlife.user.Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new com.novapetzlife.user.Role("ROLE_CUSTOMER", "Customer account")));
        com.novapetzlife.user.User user = userRepository.findByEmail("hari.review@example.com").orElseGet(() -> {
            com.novapetzlife.user.User u = new com.novapetzlife.user.User("hari.review@example.com", passwordEncoder.encode("Secret123!"), "Hari", "Krishnan", "+919447200999");
            u.setRoles(java.util.Set.of(customerRole));
            return userRepository.save(u);
        });
        testCustomer = new Customer(user, user.getEmail(), user.getPhone(), "Hari", "Krishnan", false);
        testCustomer = customerRepository.save(testCustomer);

        // 3. Category & Brand
        Category category = categoryRepository.save(new Category("Cat Nutrition", "cat-nutrition-" + suffix, "Premium cat food", null, 1));
        Brand brand = brandRepository.save(new Brand("Royal Canin", "royal-canin-" + suffix, "Veterinary Nutrition"));

        // 4. Product
        testProduct = new Product(
                brand,
                category,
                "Royal Canin Kitten Wet Food Pouch",
                "royal-canin-kitten-wet-" + suffix,
                "RC-KIT-WET-" + suffix,
                new BigDecimal("120.00"),
                new BigDecimal("110.00")
        );
        testProduct.setIsActive(true);
        testProduct = productRepository.save(testProduct);
        inventoryService.initializeStock(testProduct, null, 50, 5);
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
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "hari.review@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Product Reviews: Customer submits review, verified purchase detection marks badge, and rating summary updates")
    void testSubmitProductReviewWithVerifiedBuyerBadge() throws Exception {
        // 1. Create a DELIVERED order for this customer with testProduct to establish verified purchase
        Order order = new Order(
                "ORD-VERIF-" + UUID.randomUUID().toString().substring(0, 6),
                testCustomer,
                "DELIVERED",
                new BigDecimal("220.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("220.00"),
                "COMPLETED",
                "COD",
                "{}",
                new BigDecimal("2.50"),
                true,
                "Morning"
        );
        order = orderRepository.save(order);
        OrderItem item = new OrderItem(
                order, testProduct, null, testProduct.getName(), testProduct.getBrand().getName(),
                testProduct.getSku(), 2, new BigDecimal("110.00"), new BigDecimal("220.00"),
                new BigDecimal("18.00"), new BigDecimal("33.56"), "2309",
                "{\"returnAllowed\":true,\"returnWindowDays\":7}",
                "{\"refundAllowed\":true}"
        );
        orderItemRepository.save(item);

        // 2. Submit customer review
        CreateReviewRequest req = new CreateReviewRequest(
                testProduct.getId(),
                5,
                "Best kitten wet food in Trivandrum",
                "My 3-month-old kitten immediately devoured this gravy pouch. Excellent quality and quick delivery from Kuravankonam store!",
                List.of("https://res.cloudinary.com/novapetzlife/image/upload/sample_kitten.jpg")
        );

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.isVerifiedPurchase").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 3. Verify Product Review Summary Endpoint
        mockMvc.perform(get("/api/v1/reviews/product/" + testProduct.getId() + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.totalReviews").value(1))
                .andExpect(jsonPath("$.data.ratingDistribution['5']").value(1));

        // 4. Verify Product Reviews List Endpoint
        mockMvc.perform(get("/api/v1/reviews/product/" + testProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].customerName").value(containsString("Hari")))
                .andExpect(jsonPath("$.data[0].isVerifiedPurchase").value(true));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Admin Moderation: Admin reviews customer submission, modifies status, and appends store official response")
    void testAdminReviewModerationAndReply() throws Exception {
        // Customer leaves review
        CreateReviewRequest req = new CreateReviewRequest(
                testProduct.getId(),
                4,
                "Good product",
                "Fresh stock and well packaged.",
                null
        );

        ReviewDto created = reviewService.submitReview(testCustomer, req);
        Long reviewId = created.id();

        // Admin adds official store reply
        ModerateReviewRequest modReq = new ModerateReviewRequest("APPROVED", "Thank you Hari! We always stock the freshest batches at Kuravankonam.");
        mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/moderate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminReply").value(containsString("freshest batches at Kuravankonam")));

        // Customer views product reviews - verified reply appears
        mockMvc.perform(get("/api/v1/reviews/product/" + testProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].adminReply").value(containsString("Kuravankonam")));
    }

    @Test
    @DisplayName("Google Business: Returns verified store rating 4.8, 148 reviews, and Kuravankonam coordinates")
    void testGoogleBusinessProfileEndpoint() throws Exception {
        // Seed authentic review
        googleReviewRepository.save(new GoogleReview(
                "Anoop Nair",
                "https://example.com/avatar.jpg",
                5,
                "2 weeks ago",
                "Excellent pet store at Kuravankonam Junction. Great stock of dog food and prompt local delivery.",
                "ChIJ_anoop_01",
                true
        ));

        mockMvc.perform(get("/api/v1/google-business/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overallRating").value(4.8))
                .andExpect(jsonPath("$.data.totalUserRatings").value(148))
                .andExpect(jsonPath("$.data.latitude").value(closeTo(8.528448, 0.001)))
                .andExpect(jsonPath("$.data.longitude").value(closeTo(76.953268, 0.001)))
                .andExpect(jsonPath("$.data.featuredReviews", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Google Business Admin: Admin can trigger sync from Google Places and toggle homepage visibility")
    void testAdminGoogleSyncAndFeatureToggle() throws Exception {
        // Seed authentic review for feature toggle testing
        googleReviewRepository.save(new GoogleReview(
                "Authentic Reviewer",
                "https://example.com/avatar.jpg",
                5,
                "1 week ago",
                "Great pet accessories and reliable advice at Kuravankonam shop.",
                "ChIJ_genuine_test_01",
                true
        ));

        // Trigger sync
        mockMvc.perform(post("/api/v1/admin/google-business/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncedCount").value(greaterThanOrEqualTo(1)));

        List<GoogleReview> all = googleReviewRepository.findAll();
        assertFalse(all.isEmpty());

        Long firstId = all.get(0).getId();

        // Toggle feature off
        mockMvc.perform(patch("/api/v1/admin/google-business/reviews/" + firstId + "/feature?isFeatured=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFeatured").value(false));

        // Toggle feature back on
        mockMvc.perform(patch("/api/v1/admin/google-business/reviews/" + firstId + "/feature?isFeatured=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFeatured").value(true));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Media Management: Admin adds gallery images, sets primary cover photo, and lists images")
    void testProductImageGalleryWorkflow() throws Exception {
        // 1. Add first image (becomes primary by default)
        AddProductImageRequest img1 = new AddProductImageRequest(
                "https://res.cloudinary.com/novapetzlife/image/upload/v1/rc_kitten_front.jpg",
                "Front packaging of Royal Canin Kitten pouch",
                1,
                true
        );
        String res1 = mockMvc.perform(post("/api/v1/admin/products/" + testProduct.getId() + "/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(img1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andReturn().getResponse().getContentAsString();

        Long img1Id = objectMapper.readTree(res1).path("data").path("id").asLong();

        // 2. Add second image (back nutrition label)
        AddProductImageRequest img2 = new AddProductImageRequest(
                "https://res.cloudinary.com/novapetzlife/image/upload/v1/rc_kitten_back.jpg",
                "Nutritional composition and feeding guide",
                2,
                false
        );
        String res2 = mockMvc.perform(post("/api/v1/admin/products/" + testProduct.getId() + "/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(img2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(false))
                .andReturn().getResponse().getContentAsString();

        Long img2Id = objectMapper.readTree(res2).path("data").path("id").asLong();

        // 3. Switch primary to image 2
        mockMvc.perform(put("/api/v1/admin/products/" + testProduct.getId() + "/images/" + img2Id + "/primary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(true));

        // 4. Verify gallery list
        mockMvc.perform(get("/api/v1/admin/products/" + testProduct.getId() + "/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("Security: Unauthenticated review submission rejected with 401/403")
    void testUnauthenticatedReviewSubmission_Rejected() throws Exception {
        CreateReviewRequest req = new CreateReviewRequest(
                testProduct.getId(), 5, "Unauth review", "Should be rejected", null
        );
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "hari.review@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Security: Duplicate review by same customer for same product rejected")
    void testDuplicateReview_Rejected() {
        CreateReviewRequest req = new CreateReviewRequest(
                testProduct.getId(), 5, "First Review", "First review body", null
        );
        reviewService.submitReview(testCustomer, req);

        // Attempt second review for same product by same customer
        CreateReviewRequest dupReq = new CreateReviewRequest(
                testProduct.getId(), 4, "Duplicate Review", "Second review body", null
        );
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.submitReview(testCustomer, dupReq);
        });
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Media Security: Upload with invalid MIME type or empty file is rejected")
    void testMediaUploadValidation_Rejected() throws Exception {
        org.springframework.mock.web.MockMultipartFile emptyFile =
                new org.springframework.mock.web.MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);
        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(emptyFile))
                .andExpect(status().is4xxClientError());

        org.springframework.mock.web.MockMultipartFile invalidMime =
                new org.springframework.mock.web.MockMultipartFile("file", "malicious.exe", "application/octet-stream", "evil bytes".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(invalidMime))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("NPZ-VERIFY-010: Spoofed MIME header with non-image bytes is rejected by magic-byte check")
    void testMediaUpload_SpoofedMimeHeader_Rejected() throws Exception {
        // Client sets content-type to image/png, but file contains arbitrary non-image text
        byte[] fakeBytes = "This is a plain text file pretending to be a PNG".getBytes();
        org.springframework.mock.web.MockMultipartFile spoofedFile =
                new org.springframework.mock.web.MockMultipartFile("file", "fake.png", "image/png", fakeBytes);

        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(spoofedFile))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("NPZ-VERIFY-010: SVG/script payload disguised as JPEG is rejected")
    void testMediaUpload_SvgScriptPayload_Rejected() throws Exception {
        byte[] svgPayload = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        org.springframework.mock.web.MockMultipartFile svgFile =
                new org.springframework.mock.web.MockMultipartFile("file", "exploit.jpg", "image/jpeg", svgPayload);

        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(svgFile))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("NPZ-VERIFY-010: Path traversal sequence in upload filename is rejected")
    void testMediaUpload_PathTraversal_Rejected() throws Exception {
        byte[] validPngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
        org.springframework.mock.web.MockMultipartFile traversalFile =
                new org.springframework.mock.web.MockMultipartFile("file", "../../secret.png", "image/png", validPngHeader);

        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(traversalFile))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("NPZ-VERIFY-010: Genuine image with unconfigured Cloudinary returns honest 503 SERVICE_UNAVAILABLE stub response")
    void testMediaUpload_GenuineImage_UnconfiguredCloudinary_Returns503Stub() throws Exception {
        byte[] validPngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};
        org.springframework.mock.web.MockMultipartFile genuinePng =
                new org.springframework.mock.web.MockMultipartFile("file", "cat_food.png", "image/png", validPngHeader);

        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(genuinePng))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Cloudinary CDN media storage is unconfigured")));
    }

    @Test
    @DisplayName("Public Media: Non-existent file returns 404")
    void testPublicMedia_NonExistentFile_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/media/files/non_existent_image_12345.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Public Media: Path traversal filename is rejected")
    void testPublicMedia_PathTraversal_Rejected() throws Exception {
        mockMvc.perform(get("/api/v1/media/files/..%2Fsecret.txt"))
                .andExpect(status().is4xxClientError());
    }
}
