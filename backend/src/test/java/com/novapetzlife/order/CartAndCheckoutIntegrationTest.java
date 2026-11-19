package com.novapetzlife.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.AddToCartRequest;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.AddressCreateRequest;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.ReturnInspectionRepository;
import com.novapetzlife.returns.ReturnRequestItemRepository;
import com.novapetzlife.returns.ReturnRequestRepository;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.payment.PaymentRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartAndCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

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
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private PetProfileRepository petProfileRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private CouponRepository couponRepository;

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
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Product testProduct;

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

        // Ensure Shop location is verified
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

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Brand brand = brandRepository.save(new Brand("Arden Grange", "arden-grange-" + suffix, "Hypoallergenic pet nutrition"));
        Category category = categoryRepository.save(new Category("Dog Food", "dog-food-" + suffix, "Nutritious dog food", null, 1));

        testProduct = new Product(
                brand,
                category,
                "Arden Grange Adult Dog Fresh Chicken & Rice",
                "arden-grange-chicken-" + suffix,
                "AG-DOG-" + suffix.toUpperCase(),
                new BigDecimal("1250.00"),
                new BigDecimal("1150.00")
        );
        testProduct.setPetType("Dog");
        testProduct.setLifeStage("Adult");

        ProductReturnPolicy returnPolicy = new ProductReturnPolicy();
        returnPolicy.setProduct(testProduct);
        returnPolicy.setReturnAllowed(true);
        returnPolicy.setReturnWindowDays(10);
        returnPolicy.setRefundAllowed(true);
        returnPolicy.setOriginalPackagingRequired(true);
        returnPolicy.setUnusedConditionRequired(true);
        returnPolicy.setCustomConditions("Must have intact security seal");
        testProduct.setReturnPolicy(returnPolicy);

        testProduct = productRepository.save(testProduct);
        inventoryService.initializeStock(testProduct, null, 15, 3);
    }

    @Test
    @DisplayName("Shopping Cart: Guest adds item to cart with session token and validates stock availability")
    void testGuestCartOperations() throws Exception {
        String sessionToken = "session_" + UUID.randomUUID();
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 2, sessionToken);

        // 1. Add item
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(2300.00)) // 2 * ₹1150.00
                .andExpect(jsonPath("$.data.items[0].productName", containsString("Arden Grange")));

        // 2. Retrieve active cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("X-Session-Token", sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(15));
    }

    @Test
    @DisplayName("Order Checkout: Seamless checkout, 20 km Kuravankonam distance check, stock reservation, and return snapshot")
    void testCompleteCheckoutFlow() throws Exception {
        String sessionToken = "checkout_session_" + UUID.randomUUID();

        // 1. Add to cart
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 1, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        // 2. Perform Checkout (Pattom location ~1.1 km away from Kuravankonam store)
        AddressCreateRequest address = new AddressCreateRequest(
                "Suresh Kumar",
                "+919447123456",
                "TC 4/1202, Near Pattom Palace",
                "Pattom P.O.",
                "Opposite LIC Office",
                "Thiruvananthapuram",
                "Kerala",
                "695004",
                new BigDecimal("8.524400"),
                new BigDecimal("76.944200"),
                true
        );

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null,
                sessionToken,
                "suresh.kumar@example.com",
                "+919447123456",
                "Suresh",
                "Kumar",
                null,
                address,
                8.5244,
                76.9442,
                "Evening Slot (05:00 PM - 08:30 PM)",
                "COD",
                "Please ring bell at gate"
        );

        String responseJson = mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.isWithinRadius").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].returnPolicySnapshot", containsString("Must have intact security seal")))
                .andExpect(jsonPath("$.data.items[0].returnPolicySnapshot", containsString("\"returnWindowDays\":10")))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = objectMapper.readTree(responseJson).path("data").path("orderNumber").asText();

        // 3. Verify stock reservation: Available stock should be 15 - 1 = 14
        assertEquals(14, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 4. Verify public order tracking
        mockMvc.perform(get("/api/v1/orders/" + orderNumber + "/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.timeline", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Admin Order Management: Advance lifecycle and release stock upon order cancellation")
    void testAdminOrderStatusAndStockRelease() throws Exception {
        String sessionToken = "admin_cancel_session_" + UUID.randomUUID();

        // 1. Add item to cart and checkout
        AddToCartRequest addRequest = new AddToCartRequest(testProduct.getId(), null, 3, sessionToken);
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                null,
                sessionToken,
                "rahul@example.com",
                "+919847012345",
                "Rahul",
                "Menon",
                null,
                null,
                8.5244,
                76.9442,
                "Morning Slot (09:00 AM - 01:00 PM)",
                "COD",
                null
        );

        String responseJson = mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseJson).path("data").path("id").asLong();

        // Available stock reserved: 15 - 3 = 12
        assertEquals(12, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 2. Admin advances to PACKED
        OrderStatusUpdateRequest processingReq = new OrderStatusUpdateRequest("PACKED", "Order packed at fulfillment station");
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(processingReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PACKED"));

        // 3. Admin cancels order -> Reserved stock should be released back!
        OrderStatusUpdateRequest cancelReq = new OrderStatusUpdateRequest("CANCELLED", "Customer requested cancellation");
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));

        // Available stock restored back to 15!
        assertEquals(15, inventoryService.getAvailableStock(testProduct.getId(), null));
    }
}
