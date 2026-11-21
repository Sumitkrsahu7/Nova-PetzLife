package com.novapetzlife.promotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.Cart;
import com.novapetzlife.cart.CartItem;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.AddressCreateRequest;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.petprofile.PetProfile;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.promotion.dto.ApplyCouponRequest;
import com.novapetzlife.promotion.dto.CreateCouponRequest;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.ReviewImageRepository;
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
import java.time.Instant;
import java.time.LocalDate;
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
class PromotionAndPetProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

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
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

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
    private NotificationRepository notificationRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Customer testCustomer;
    private Product dogFoodProduct;
    private Product catFoodProduct;

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
        User user = userRepository.findByEmail("arun.promo@example.com").orElseGet(() ->
                userRepository.save(new User("arun.promo@example.com", "Password@123", "Arun", "Kumar", "+9194471" + suffix))
        );
        testCustomer = customerService.getOrCreateCustomerForUser(user);

        // 3. Category & Brand
        Category cat = categoryRepository.save(new Category("Pet Nutrition", "pet-nutrition-" + suffix, "Pet Nutrition", null, 1));
        Brand brand = brandRepository.save(new Brand("Royal Pet", "royal-pet-" + suffix, "Premium Pet Nutrition"));

        // 4. Products for Pet Type & Life Stage Matching
        dogFoodProduct = new Product(
                brand,
                cat,
                "Royal Canin Maxi Adult Dog Food",
                "royal-canin-maxi-adult-dog-food-" + suffix,
                "RC-DOG-" + suffix,
                new BigDecimal("1200.00"),
                new BigDecimal("1200.00")
        );
        dogFoodProduct.setPetType("Dog");
        dogFoodProduct.setLifeStage("Adult");
        ProductReturnPolicy dogPolicy = new ProductReturnPolicy();
        dogPolicy.setProduct(dogFoodProduct);
        dogPolicy.setReturnAllowed(true);
        dogPolicy.setReturnWindowDays(7);
        dogPolicy.setRefundAllowed(true);
        dogFoodProduct.setReturnPolicy(dogPolicy);
        dogFoodProduct.setIsActive(true);
        dogFoodProduct = productRepository.save(dogFoodProduct);
        inventoryService.initializeStock(dogFoodProduct, null, 50, 5);

        catFoodProduct = new Product(
                brand,
                cat,
                "Whiskas Kitten Ocean Fish Dry Food",
                "whiskas-kitten-ocean-fish-" + suffix,
                "WH-CAT-" + suffix,
                new BigDecimal("450.00"),
                new BigDecimal("450.00")
        );
        catFoodProduct.setPetType("Cat");
        catFoodProduct.setLifeStage("Kitten");
        ProductReturnPolicy catPolicy = new ProductReturnPolicy();
        catPolicy.setProduct(catFoodProduct);
        catPolicy.setReturnAllowed(false);
        catPolicy.setReturnWindowDays(0);
        catPolicy.setRefundAllowed(false);
        catFoodProduct.setReturnPolicy(catPolicy);
        catFoodProduct.setIsActive(true);
        catFoodProduct = productRepository.save(catFoodProduct);
        inventoryService.initializeStock(catFoodProduct, null, 40, 5);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Admin: Create and list promotional discount coupons")
    void testAdminCreateAndListCoupons() throws Exception {
        CreateCouponRequest request = new CreateCouponRequest(
                "MONSOON20",
                "20% off on all pet foods for orders above 800",
                "PERCENTAGE",
                new BigDecimal("20.00"),
                new BigDecimal("800.00"),
                new BigDecimal("250.00"),
                100,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400 * 30),
                true
        );

        mockMvc.perform(post("/api/v1/admin/promotions/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("MONSOON20"))
                .andExpect(jsonPath("$.data.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.discountValue").value(20.00));

        // List coupons
        mockMvc.perform(get("/api/v1/admin/promotions/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code").value("MONSOON20"));
    }

    @Test
    @DisplayName("Public: Discover active coupons and validate eligibility against cart subtotal")
    void testPublicActiveCouponsAndValidation() throws Exception {
        // Create an active coupon
        Coupon coupon = new Coupon(
                "WELCOME100",
                "Flat 100 off on first order",
                "FIXED",
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                500,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400 * 30),
                true
        );
        couponRepository.save(coupon);

        // 1. Get active coupons
        mockMvc.perform(get("/api/v1/promotions/coupons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("WELCOME100"));

        // 2. Validate valid cart subtotal (₹1200 >= ₹500 min order)
        ApplyCouponRequest validReq = new ApplyCouponRequest("WELCOME100", new BigDecimal("1200.00"), testCustomer.getId());
        mockMvc.perform(post("/api/v1/promotions/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                .andExpect(jsonPath("$.data.newSubtotal").value(1100.00));

        // 3. Validate below min order threshold (₹300 < ₹500 min order)
        ApplyCouponRequest invalidReq = new ApplyCouponRequest("WELCOME100", new BigDecimal("300.00"), testCustomer.getId());
        mockMvc.perform(post("/api/v1/promotions/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.message", containsString("Minimum order amount")));
    }

    @Test
    @WithMockUser(username = "arun.promo@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Checkout: Apply coupon code, deduct discount, and record coupon usage")
    void testCheckoutWithCouponDeduction() throws Exception {
        // Setup coupon
        Coupon coupon = new Coupon(
                "SAVE100",
                "Flat 100 off",
                "FIXED",
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                100,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400 * 30),
                true
        );
        couponRepository.save(coupon);

        // Setup Cart with Dog Food (₹1200.00)
        String sessionToken = "session-promo-" + UUID.randomUUID();
        Cart cart = cartRepository.save(new Cart(testCustomer, sessionToken));
        cartItemRepository.save(new CartItem(cart, dogFoodProduct, null, 1, new BigDecimal("1200.00")));

        // Checkout Payload with coupon SAVE100
        AddressCreateRequest address = new AddressCreateRequest(
                "Arun Kumar",
                "+919447100099",
                "Pattom Palace Road",
                null,
                null,
                "Thiruvananthapuram",
                "Kerala",
                "695004",
                new BigDecimal("8.5244"),
                new BigDecimal("76.9442"),
                true
        );

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(),
                sessionToken,
                "arun@example.com",
                "+919447100099",
                "Arun",
                "Kumar",
                null,
                address,
                8.5244,
                76.9442,
                "Evening (4:00 PM - 8:00 PM)",
                "COD",
                "SAVE100",
                "Fast delivery please"
        );

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").exists())
                .andExpect(jsonPath("$.data.totalAmount").value(1200.00))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                // Free delivery for orders >= 500, so deliveryCharge = 0.00
                // finalPayable = 1200.00 - 100.00 = 1100.00
                .andExpect(jsonPath("$.data.finalPayableAmount").value(1100.00));

        // Verify Coupon Usage record was saved in DB
        List<CouponUsage> usages = couponUsageRepository.findAll();
        assertEquals(1, usages.size());
        assertEquals("SAVE100", usages.get(0).getCoupon().getCode());
        assertEquals(new BigDecimal("100.00"), usages.get(0).getDiscountApplied());

        // Verify coupon current usage count incremented
        Coupon updatedCoupon = couponRepository.findByCodeIgnoreCase("SAVE100").orElseThrow();
        assertEquals(1, updatedCoupon.getUsageCount());
    }

    @Test
    @DisplayName("Pet Profiles: Customer can register, list, update and delete pets")
    void testCustomerPetProfileCrud() throws Exception {
        CreatePetProfileRequest req = new CreatePetProfileRequest(
                "Bruno",
                "Dog",
                "Golden Retriever",
                LocalDate.of(2023, 4, 15),
                "Male",
                new BigDecimal("28.5"),
                "Adult",
                "Salmon kibble",
                "Chicken sensitivity"
        );

        // 1. Create Pet Profile
        mockMvc.perform(post("/api/v1/customer/pets")
                        .param("customerId", testCustomer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.petName").value("Bruno"))
                .andExpect(jsonPath("$.data.petType").value("Dog"))
                .andExpect(jsonPath("$.data.lifeStage").value("Adult"));

        // 2. List Pets
        mockMvc.perform(get("/api/v1/customer/pets")
                        .param("customerId", testCustomer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].petName").value("Bruno"));

        Long petId = petProfileRepository.findAll().get(0).getId();

        // 3. Update Pet Profile
        req.setWeightKg(new BigDecimal("29.2"));
        mockMvc.perform(put("/api/v1/customer/pets/" + petId)
                        .param("customerId", testCustomer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightKg").value(29.2));

        // 4. Delete Pet Profile
        mockMvc.perform(delete("/api/v1/customer/pets/" + petId)
                        .param("customerId", testCustomer.getId().toString()))
                .andExpect(status().isOk());

        assertEquals(0, petProfileRepository.count());
    }

    @Test
    @DisplayName("Pet Nutrition Recommendations: Suggests products matching pet species and life stage")
    void testPersonalizedPetRecommendations() throws Exception {
        // Register an Adult Dog
        PetProfile pet = new PetProfile(
                testCustomer,
                "Rocky",
                "Dog",
                "German Shepherd",
                LocalDate.of(2022, 1, 10),
                "Male",
                new BigDecimal("32.0"),
                "Adult",
                "High protein",
                "Grain sensitivity"
        );
        pet = petProfileRepository.save(pet);

        // Fetch Recommendations for Rocky
        mockMvc.perform(get("/api/v1/customer/pets/" + pet.getId() + "/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.petName").value("Rocky"))
                .andExpect(jsonPath("$.data.petType").value("Dog"))
                .andExpect(jsonPath("$.data.lifeStage").value("Adult"))
                .andExpect(jsonPath("$.data.rationale").isNotEmpty())
                // Royal Canin Maxi Adult Dog Food should be recommended
                .andExpect(jsonPath("$.data.recommendedProducts", hasSize(1)))
                .andExpect(jsonPath("$.data.recommendedProducts[0].name").value("Royal Canin Maxi Adult Dog Food"))
                .andExpect(jsonPath("$.data.recommendedProducts[0].petType").value("Dog"))
                // Whiskas Kitten (Cat) should NOT be recommended for Rocky
                .andExpect(jsonPath("$.data.recommendedProducts[0].name", not("Whiskas Kitten Ocean Fish Dry Food")));
    }
}
