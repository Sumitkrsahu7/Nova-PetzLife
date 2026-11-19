package com.novapetzlife.returns;

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
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import com.novapetzlife.returns.dto.CancelOrderRequest;
import com.novapetzlife.returns.dto.CreateReturnRequest;
import com.novapetzlife.returns.dto.InspectReturnRequest;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.tax.InvoiceRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.review.ReviewRepository;
import com.novapetzlife.review.ReviewImageRepository;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.promotion.CouponUsageRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReturnAndRefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Customer testCustomer;
    private Product returnableProduct;
    private Product nonReturnableProduct;
    private Order deliveredOrder;
    private Order pendingOrder;

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

        // 1. Shop Location
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
        testCustomer = new Customer(null, "rohit." + suffix + "@example.com", "+9198471" + suffix, "Rohit", "Varma", true);
        testCustomer = customerRepository.save(testCustomer);

        // 3. Brands & Categories
        Brand brand = brandRepository.save(new Brand("Kong", "kong-" + suffix, "Durable pet toys & gear"));
        Category category = categoryRepository.save(new Category("Pet Toys", "pet-toys-" + suffix, "Dog toys and accessories", null, 1));

        // 4. Products: One returnable (toy), One non-returnable (treat)
        returnableProduct = new Product(
                brand, category, "Kong Classic Dog Toy Large", "kong-classic-large-" + suffix, "KONG-L-" + suffix,
                new BigDecimal("999.00"), new BigDecimal("899.00")
        );
        ProductReturnPolicy returnablePolicy = new ProductReturnPolicy();
        returnablePolicy.setProduct(returnableProduct);
        returnablePolicy.setReturnAllowed(true);
        returnablePolicy.setReturnWindowDays(7);
        returnablePolicy.setRefundAllowed(true);
        returnableProduct.setReturnPolicy(returnablePolicy);
        returnableProduct = productRepository.save(returnableProduct);
        inventoryService.initializeStock(returnableProduct, null, 10, 2);

        nonReturnableProduct = new Product(
                brand, category, "Organic Dog Chew Sticks", "organic-dog-chews-" + suffix, "CHEW-" + suffix,
                new BigDecimal("350.00"), new BigDecimal("299.00")
        );
        ProductReturnPolicy nonReturnablePolicy = new ProductReturnPolicy();
        nonReturnablePolicy.setProduct(nonReturnableProduct);
        nonReturnablePolicy.setReturnAllowed(false);
        nonReturnablePolicy.setReturnWindowDays(0);
        nonReturnablePolicy.setRefundAllowed(false);
        nonReturnableProduct.setReturnPolicy(nonReturnablePolicy);
        nonReturnableProduct = productRepository.save(nonReturnableProduct);
        inventoryService.initializeStock(nonReturnableProduct, null, 20, 5);

        // 5. Delivered Order
        deliveredOrder = new Order();
        deliveredOrder.setOrderNumber("ORD-DELIV-" + suffix.toUpperCase());
        deliveredOrder.setCustomer(testCustomer);
        deliveredOrder.setOrderStatus("DELIVERED");
        deliveredOrder.setPaymentStatus("COMPLETED");
        deliveredOrder.setPaymentMethod("RAZORPAY");
        deliveredOrder.setTotalAmount(new BigDecimal("1198.00"));
        deliveredOrder.setDiscountAmount(BigDecimal.ZERO);
        deliveredOrder.setDeliveryCharge(BigDecimal.ZERO);
        deliveredOrder.setTaxAmount(new BigDecimal("182.75"));
        deliveredOrder.setFinalPayableAmount(new BigDecimal("1198.00"));
        deliveredOrder.setShopDistanceKm(new BigDecimal("2.50"));
        deliveredOrder.setIsWithinRadius(true);
        deliveredOrder.setDeliverySlot("Evening (4:00 PM - 8:00 PM)");
        deliveredOrder.setDeliveryAddressJson("{\"recipientName\":\"Rohit Varma\",\"city\":\"Thiruvananthapuram\"}");

        OrderItem item1 = new OrderItem(
                deliveredOrder, returnableProduct, null, returnableProduct.getName(), brand.getName(),
                returnableProduct.getSku(), 1, returnableProduct.getDiscountedPrice(), returnableProduct.getDiscountedPrice(),
                new BigDecimal("18.00"), new BigDecimal("137.14"), "9503",
                "{\"returnAllowed\":true,\"returnWindowDays\":7,\"originalPackagingRequired\":true}",
                "{\"refundAllowed\":true}"
        );
        OrderItem item2 = new OrderItem(
                deliveredOrder, nonReturnableProduct, null, nonReturnableProduct.getName(), brand.getName(),
                nonReturnableProduct.getSku(), 1, nonReturnableProduct.getDiscountedPrice(), nonReturnableProduct.getDiscountedPrice(),
                new BigDecimal("18.00"), new BigDecimal("45.61"), "2309",
                "{\"returnAllowed\":false,\"returnWindowDays\":0,\"originalPackagingRequired\":true}",
                "{\"refundAllowed\":false}"
        );
        deliveredOrder.addItem(item1);
        deliveredOrder.addItem(item2);
        deliveredOrder = orderRepository.save(deliveredOrder);

        // Record payment for delivered order
        paymentRepository.save(new Payment(deliveredOrder, "order_rzp_deliv_test", deliveredOrder.getFinalPayableAmount(), "SUCCESS", "RAZORPAY"));

        // 6. Pending Order for Cancellation Testing
        pendingOrder = new Order();
        pendingOrder.setOrderNumber("ORD-PEND-" + suffix.toUpperCase());
        pendingOrder.setCustomer(testCustomer);
        pendingOrder.setOrderStatus("CONFIRMED");
        pendingOrder.setPaymentStatus("COMPLETED");
        pendingOrder.setPaymentMethod("RAZORPAY");
        pendingOrder.setTotalAmount(new BigDecimal("899.00"));
        pendingOrder.setDiscountAmount(BigDecimal.ZERO);
        pendingOrder.setDeliveryCharge(BigDecimal.ZERO);
        pendingOrder.setTaxAmount(new BigDecimal("137.14"));
        pendingOrder.setFinalPayableAmount(new BigDecimal("899.00"));
        pendingOrder.setShopDistanceKm(new BigDecimal("1.50"));
        pendingOrder.setIsWithinRadius(true);
        pendingOrder.setDeliveryAddressJson("{\"recipientName\":\"Rohit Varma\",\"city\":\"Thiruvananthapuram\"}");

        OrderItem itemPending = new OrderItem(
                pendingOrder, returnableProduct, null, returnableProduct.getName(), brand.getName(),
                returnableProduct.getSku(), 1, returnableProduct.getDiscountedPrice(), returnableProduct.getDiscountedPrice(),
                new BigDecimal("18.00"), new BigDecimal("137.14"), "9503",
                "{\"returnAllowed\":true,\"returnWindowDays\":7}",
                "{\"refundAllowed\":true}"
        );
        pendingOrder.addItem(itemPending);
        pendingOrder = orderRepository.save(pendingOrder);

        // Reserve 1 unit for pending order
        inventoryService.reserveStock(returnableProduct.getId(), null, 1, pendingOrder.getOrderNumber());
        paymentRepository.save(new Payment(pendingOrder, "order_rzp_pend_test", pendingOrder.getFinalPayableAmount(), "SUCCESS", "RAZORPAY"));
    }

    @Test
    @DisplayName("Should evaluate return eligibility per item comparing purchase snapshot and delivery status")
    void testCheckReturnEligibility_DeliveredOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + deliveredOrder.getOrderNumber() + "/return-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.canRequestReturn", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                // First item (Toy) is eligible
                .andExpect(jsonPath("$.data.items[0].productName", is("Kong Classic Dog Toy Large")))
                .andExpect(jsonPath("$.data.items[0].returnAllowed", is(true)))
                .andExpect(jsonPath("$.data.items[0].eligible", is(true)))
                // Second item (Consumable treats) is ineligible
                .andExpect(jsonPath("$.data.items[1].productName", is("Organic Dog Chew Sticks")))
                .andExpect(jsonPath("$.data.items[1].returnAllowed", is(false)))
                .andExpect(jsonPath("$.data.items[1].eligible", is(false)))
                .andExpect(jsonPath("$.data.items[1].reasonIfNotEligible", containsString("non-returnable")));
    }

    @Test
    @DisplayName("Should reject return request on un-delivered orders")
    void testCheckReturnEligibility_UndeliveredOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + pendingOrder.getOrderNumber() + "/return-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.canRequestReturn", is(false)))
                .andExpect(jsonPath("$.data.generalEligibilityMessage", containsString("DELIVERED orders")));
    }

    @Test
    @DisplayName("Should submit return request for eligible items and assign unique RET- request number")
    void testSubmitReturnRequest_Success() throws Exception {
        OrderItem eligibleItem = deliveredOrder.getItems().stream()
                .filter(it -> it.getProductName().contains("Kong"))
                .findFirst()
                .orElseThrow();

        CreateReturnRequest request = new CreateReturnRequest(
                deliveredOrder.getOrderNumber(),
                List.of(new CreateReturnRequest.ReturnItemRequest(eligibleItem.getId(), 1)),
                "Wrong item or size delivered",
                "Product box was undamaged but too large for puppy"
        );

        mockMvc.perform(post("/api/v1/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.requestNumber", startsWith("RET-")))
                .andExpect(jsonPath("$.data.status", is("REQUESTED")))
                .andExpect(jsonPath("$.data.estimatedRefundAmount", is(899.00)))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        // Verify in DB
        List<ReturnRequest> savedList = returnRequestRepository.findByOrderId(deliveredOrder.getId());
        assertEquals(1, savedList.size());
        assertEquals("REQUESTED", savedList.get(0).getStatus());
    }

    @Test
    @DisplayName("Should cancel confirmed order prior to delivery, release reserved stock, and initiate refund")
    void testCancelOrderBeforeDelivery_Success() throws Exception {
        int initialAvailable = inventoryService.getAvailableStock(returnableProduct.getId(), null);

        CancelOrderRequest request = new CancelOrderRequest("Customer requested cancellation");

        mockMvc.perform(post("/api/v1/orders/" + pendingOrder.getOrderNumber() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CANCELLED")));

        // Order status verified in DB
        Order updated = orderRepository.findByOrderNumber(pendingOrder.getOrderNumber()).orElseThrow();
        assertEquals("CANCELLED", updated.getOrderStatus());
        assertEquals("REFUNDED", updated.getPaymentStatus());

        // Stock released back to available inventory
        int newAvailable = inventoryService.getAvailableStock(returnableProduct.getId(), null);
        assertEquals(initialAvailable + 1, newAvailable);

        // Refund verified in DB
        List<Refund> refunds = refundRepository.findByOrderId(pendingOrder.getId());
        assertFalse(refunds.isEmpty());
        assertEquals(new BigDecimal("899.00"), refunds.get(0).getAmount());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Admin workflow: Schedule pickup, mark received, inspect item, restock inventory and approve refund")
    void testAdminReturnWorkflow_FullLifecycle() throws Exception {
        OrderItem eligibleItem = deliveredOrder.getItems().get(0);
        ReturnRequest req = new ReturnRequest(
                "RET-20261114-TEST99", deliveredOrder, testCustomer, "Damaged in transit", "Package arrived opened"
        );
        req.addItem(new ReturnRequestItem(req, eligibleItem, 1));
        req = returnRequestRepository.save(req);

        int initialStock = inventoryService.getAvailableStock(returnableProduct.getId(), null);

        // 1. Admin schedules pickup
        mockMvc.perform(post("/api/v1/admin/returns/" + req.getId() + "/pickup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PICKUP_SCHEDULED")));

        // 2. Admin marks received at store
        mockMvc.perform(post("/api/v1/admin/returns/" + req.getId() + "/receive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RECEIVED")));

        // 3. Admin physical inspection at Kuravankonam shop with approved restock & refund
        InspectReturnRequest inspectRequest = new InspectReturnRequest(
                "APPROVED",
                "Product inspected at Kuravankonam store: package intact, restock verified.",
                true,
                true,
                new BigDecimal("899.00"),
                "Inspection approved by store manager"
        );

        mockMvc.perform(post("/api/v1/admin/returns/" + req.getId() + "/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inspectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        // Verify inventory restocked
        int postInspectStock = inventoryService.getAvailableStock(returnableProduct.getId(), null);
        assertEquals(initialStock + 1, postInspectStock);

        // Verify refund processed
        List<Refund> refunds = refundRepository.findByOrderId(deliveredOrder.getId());
        assertFalse(refunds.isEmpty());
        assertEquals(new BigDecimal("899.00"), refunds.get(0).getAmount());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Admin workflow: Rejected physical inspection does not restock stock and does not issue refund")
    void testAdminReturnWorkflow_RejectedInspection() throws Exception {
        OrderItem eligibleItem = deliveredOrder.getItems().get(0);
        ReturnRequest req = new ReturnRequest(
                "RET-20261114-REJ01", deliveredOrder, testCustomer, "Wrong item", "Customer claims wrong size"
        );
        req.addItem(new ReturnRequestItem(req, eligibleItem, 1));
        req.setStatus("RECEIVED");
        req = returnRequestRepository.save(req);

        int initialStock = inventoryService.getAvailableStock(returnableProduct.getId(), null);

        InspectReturnRequest inspectRequest = new InspectReturnRequest(
                "REJECTED",
                "Item was used, scratched, and packaging was destroyed by customer pet.",
                false,
                false,
                null,
                "Return policy violation: unused condition required"
        );

        mockMvc.perform(post("/api/v1/admin/returns/" + req.getId() + "/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inspectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        // Stock unchanged
        int currentStock = inventoryService.getAvailableStock(returnableProduct.getId(), null);
        assertEquals(initialStock, currentStock);

        // No refund created
        List<Refund> refunds = refundRepository.findByOrderId(deliveredOrder.getId());
        assertTrue(refunds.isEmpty());
    }
}
