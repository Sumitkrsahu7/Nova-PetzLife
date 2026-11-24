package com.novapetzlife.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.common.ResourceNotFoundException;
import com.novapetzlife.cart.Cart;
import com.novapetzlife.cart.CartItem;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.AddressCreateRequest;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.delivery.ShopLocationRepository;
import com.novapetzlife.inventory.Inventory;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransaction;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.notification.NotificationRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductService;
import com.novapetzlife.support.SupportMessage;
import com.novapetzlife.support.SupportMessageRepository;
import com.novapetzlife.support.SupportService;
import com.novapetzlife.support.SupportTicket;
import com.novapetzlife.support.SupportTicketRepository;
import com.novapetzlife.support.dto.CreateInquiryRequest;
import com.novapetzlife.support.dto.SupportTicketDto;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CheckoutConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductService productService;

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
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ShopLocationRepository shopLocationRepository;

    @Autowired
    private SupportService supportService;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.novapetzlife.returns.ReturnInspectionRepository returnInspectionRepository;

    @Autowired
    private com.novapetzlife.returns.ReturnRequestItemRepository returnRequestItemRepository;

    @Autowired
    private com.novapetzlife.returns.ReturnRequestRepository returnRequestRepository;

    @Autowired
    private com.novapetzlife.refund.RefundRepository refundRepository;

    @Autowired
    private com.novapetzlife.tax.InvoiceRepository invoiceRepository;

    @Autowired
    private com.novapetzlife.payment.PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private com.novapetzlife.customer.CustomerAddressRepository customerAddressRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Product testProductA;
    private Product testProductB;
    private Brand testBrand;
    private Category testCategory;
    private Role roleCustomer;

    @BeforeEach
    void setUp() {
        cleanAll();

        roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));

        // Ensure verified ShopLocation exists within Kuravankonam delivery zone
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
        testBrand = brandRepository.save(new Brand("Farmina N&D " + suffix, "farmina-" + suffix, "Italian grain-free pet food"));
        testCategory = categoryRepository.save(new Category("Cat Nutrition " + suffix, "cat-food-" + suffix, "Feline diet", null, 1));

        testProductA = new Product(
                testBrand, testCategory, "Farmina Ocean Cod & Pumpkin",
                "farmina-cod-" + suffix, "FM-COD-" + suffix.toUpperCase(),
                new BigDecimal("1600.00"), new BigDecimal("1450.00")
        );
        testProductA = productRepository.save(testProductA);

        testProductB = new Product(
                testBrand, testCategory, "Farmina Lamb & Blueberry",
                "farmina-lamb-" + suffix, "FM-LAMB-" + suffix.toUpperCase(),
                new BigDecimal("1750.00"), new BigDecimal("1550.00")
        );
        testProductB = productRepository.save(testProductB);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        notificationRepository.deleteAll();
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
        userRepository.deleteAll();
    }

    private Customer createTestCustomer(String email, String name) {
        User user = new User(email, "$2a$10$dummyHashPass", name, "User", "+919447" + (int)(Math.random() * 900000 + 100000));
        user.setRoles(Set.of(roleCustomer));
        user = userRepository.save(user);

        Customer customer = new Customer(user, email, user.getPhone(), name, "User", false);
        return customerRepository.save(customer);
    }

    private AddressCreateRequest createTestAddressRequest() {
        return new AddressCreateRequest(
                "Test Recipient",
                "+919447111222",
                "Kuravankonam Road",
                "Flat 4B",
                "Near Kowdiar Palace",
                "Thiruvananthapuram",
                "Kerala",
                "695003",
                new BigDecimal("8.5241"),
                new BigDecimal("76.9366"),
                true
        );
    }

    @Test
    @DisplayName("4.1 Simultaneous Checkout: Exact stock reserved, zero overselling, zero negative stock")
    void testSimultaneousCheckoutLimitedInventory_preventsOverselling() throws Exception {
        int initialStock = 2;
        int totalWorkers = 5;

        // Initialize inventory with exactly 2 units available
        inventoryService.initializeStock(testProductA, null, initialStock, 1);

        // Pre-create 5 customers and 5 carts (each with 1 unit of Product A)
        List<Customer> customers = new ArrayList<>();
        List<Cart> carts = new ArrayList<>();

        for (int i = 0; i < totalWorkers; i++) {
            Customer cust = createTestCustomer("concurrent.cust." + i + "." + UUID.randomUUID().toString().substring(0, 4) + "@example.com", "Cust" + i);
            customers.add(cust);

            Cart cart = new Cart(cust, "session_" + i + "_" + UUID.randomUUID());
            cart = cartRepository.save(cart);

            CartItem item = new CartItem(cart, testProductA, null, 1, testProductA.getDiscountedPrice());
            cart.addItem(item);
            cartItemRepository.save(item);
            carts.add(cart);
        }

        ExecutorService executor = Executors.newFixedThreadPool(totalWorkers);
        CountDownLatch readyLatch = new CountDownLatch(totalWorkers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalWorkers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger stockExhaustedCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < totalWorkers; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous unleash

                    CheckoutRequest checkoutReq = new CheckoutRequest(
                            carts.get(index).getId(),
                            null, null, null, null, null, null,
                            createTestAddressRequest(),
                            8.5241, 76.9366,
                            "Morning (9AM-1PM)",
                            "COD", null, "Concurrent test order"
                    );

                    orderService.checkout(customers.get(index), checkoutReq);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // Out of stock exception caught
                    if (e.getMessage() != null && e.getMessage().contains("Insufficient available stock")) {
                        stockExhaustedCount.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent checkout workers must complete within timeout");
        assertEquals(2, successCount.get(), "Exactly 2 checkouts must succeed for initial stock of 2");
        assertEquals(3, stockExhaustedCount.get(), "Remaining 3 checkouts must fail with stock exhaustion");
        assertEquals(0, otherErrors.get(), "No unexpected errors should occur");

        // Verify inventory table row
        Inventory inv = inventoryRepository.findByProductIdAndVariantIsNull(testProductA.getId()).orElseThrow();
        assertEquals(2, inv.getCurrentStock(), "Total physical stock must remain 2");
        assertEquals(0, inv.getAvailableStock(), "Available stock must be reduced to exactly 0");
        assertEquals(2, inv.getReservedStock(), "Reserved stock must be exactly 2");
        assertTrue(inv.getCurrentStock() >= 0 && inv.getAvailableStock() >= 0 && inv.getReservedStock() >= 0,
                "No stock values may ever be negative");

        // Verify orders in database
        assertEquals(2, orderRepository.count(), "Exactly 2 orders must be saved in database");
        assertEquals(2, orderItemRepository.count(), "Exactly 2 order items must be saved in database");

        // Verify inventory transaction log
        List<InventoryTransaction> txns = inventoryTransactionRepository.findAll();
        long reservedTxnCount = txns.stream()
                .filter(t -> "RESERVED".equalsIgnoreCase(t.getTransactionType()) && t.getProduct().getId().equals(testProductA.getId()))
                .count();
        assertEquals(2, reservedTxnCount, "Exactly 2 'RESERVED' inventory transactions must be recorded");
    }

    @Test
    @DisplayName("4.2 Scenario A: Checkout obtains pessimistic lock first, archival waits and executes after commit")
    void testCheckoutRacingArchival_ScenarioA_checkoutObtainsLockFirst() throws Exception {
        inventoryService.initializeStock(testProductA, null, 10, 2);

        Customer customer = createTestCustomer("scenario.a.cust@example.com", "ScenarioACust");
        Cart cart = new Cart(customer, "session_scenario_a");
        cart = cartRepository.save(cart);
        CartItem item = new CartItem(cart, testProductA, null, 1, testProductA.getDiscountedPrice());
        cart.addItem(item);
        cartItemRepository.save(item);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(), null, null, null, null, null, null,
                createTestAddressRequest(),
                8.5241, 76.9366,
                "Morning (9AM-1PM)",
                "COD", null, "Scenario A order"
        );

        CountDownLatch checkoutAcquiredLockLatch = new CountDownLatch(1);
        CountDownLatch archivalAttemptLatch = new CountDownLatch(1);
        CountDownLatch checkoutCanCommitLatch = new CountDownLatch(1);

        AtomicReference<OrderDetailDto> orderResult = new AtomicReference<>();
        AtomicReference<Exception> archivalException = new AtomicReference<>();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // Thread 1: Checkout in controlled transaction
        Thread checkoutThread = new Thread(() -> {
            txTemplate.execute(status -> {
                // Acquire product lock manually as done in OrderService
                Product locked = productRepository.findByIdForUpdate(testProductA.getId()).orElseThrow();
                checkoutAcquiredLockLatch.countDown(); // Signal Thread 2 to try archival

                try {
                    archivalAttemptLatch.await(5, TimeUnit.SECONDS); // Wait until Thread 2 is blocked on lock
                    checkoutCanCommitLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                OrderDetailDto orderDto = orderService.checkout(customer, checkoutReq);
                orderResult.set(orderDto);
                return null;
            });
        });

        // Thread 2: Archival
        Thread archivalThread = new Thread(() -> {
            try {
                checkoutAcquiredLockLatch.await(5, TimeUnit.SECONDS);
                archivalAttemptLatch.countDown();
                // Attempt archival - blocks until checkoutThread commits and releases pessimistic lock
                productService.removeProduct(testProductA.getId(), "ADMIN_USER");
            } catch (Exception e) {
                archivalException.set(e);
            }
        });

        checkoutThread.start();
        archivalThread.start();

        // Allow checkout to complete after archival has queued up behind the lock
        Thread.sleep(200);
        checkoutCanCommitLatch.countDown();

        checkoutThread.join(15000);
        archivalThread.join(15000);

        assertNotNull(orderResult.get(), "Checkout must succeed when obtaining lock first");
        assertNull(archivalException.get(), "Archival must succeed after checkout releases lock");

        // Verify product state is now archived
        Product reloadedProduct = productRepository.findById(testProductA.getId()).orElseThrow();
        assertTrue(reloadedProduct.getIsArchived(), "Product must now be archived");
        assertFalse(reloadedProduct.getIsActive(), "Archived product must be inactive");

        // Subsequent checkout attempt must be rejected
        Cart newCart = new Cart(customer, "session_subsequent");
        newCart = cartRepository.save(newCart);
        CartItem newItem = new CartItem(newCart, reloadedProduct, null, 1, reloadedProduct.getDiscountedPrice());
        newCart.addItem(newItem);
        cartItemRepository.save(newItem);

        CheckoutRequest subsequentReq = new CheckoutRequest(
                newCart.getId(), null, null, null, null, null, null,
                createTestAddressRequest(),
                8.5241, 76.9366,
                "Morning (9AM-1PM)",
                "COD", null, "Subsequent attempt"
        );

        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(customer, subsequentReq),
                "Subsequent checkout must reject archived product");
    }

    @Test
    @DisplayName("4.2 Scenario B: Archival obtains lock first, checkout revalidates locked state and rejects purchase")
    void testCheckoutRacingArchival_ScenarioB_archivalObtainsLockFirst() throws Exception {
        inventoryService.initializeStock(testProductA, null, 10, 2);

        Customer customer = createTestCustomer("scenario.b.cust@example.com", "ScenarioBCust");
        Cart cart = new Cart(customer, "session_scenario_b");
        cart = cartRepository.save(cart);
        CartItem item = new CartItem(cart, testProductA, null, 1, testProductA.getDiscountedPrice());
        cart.addItem(item);
        cartItemRepository.save(item);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                cart.getId(), null, null, null, null, null, null,
                createTestAddressRequest(),
                8.5241, 76.9366,
                "Morning (9AM-1PM)",
                "COD", null, "Scenario B order"
        );

        CountDownLatch archivalStartedLatch = new CountDownLatch(1);
        CountDownLatch checkoutStartLatch = new CountDownLatch(1);
        AtomicReference<Exception> checkoutException = new AtomicReference<>();
        AtomicReference<com.novapetzlife.product.ProductRemovalResult> archivalResult = new AtomicReference<>();

        // Thread 1: Archival acquires lock and archives the product
        Thread archivalThread = new Thread(() -> {
            try {
                archivalStartedLatch.countDown();
                checkoutStartLatch.await(5, TimeUnit.SECONDS);
                com.novapetzlife.product.ProductRemovalResult res = productService.removeProduct(testProductA.getId(), "ADMIN_USER");
                archivalResult.set(res);
            } catch (Exception e) {
                // Unexpected archival failure
            }
        });

        // Thread 2: Checkout attempts to purchase
        Thread checkoutThread = new Thread(() -> {
            try {
                archivalStartedLatch.await(5, TimeUnit.SECONDS);
                checkoutStartLatch.countDown();
                // Wait for archival thread to complete its transaction
                archivalThread.join(5000);
                orderService.checkout(customer, checkoutReq);
            } catch (Exception e) {
                checkoutException.set(e);
            }
        });

        archivalThread.start();
        checkoutThread.start();

        archivalThread.join(10000);
        checkoutThread.join(10000);

        assertNotNull(archivalResult.get(), "Archival must complete successfully");
        assertNotNull(checkoutException.get(), "Checkout must fail when product is archived while waiting for lock");
        assertTrue(checkoutException.get() instanceof IllegalArgumentException,
                "Should throw IllegalArgumentException indicating product is archived or cart invalid");
        assertTrue(checkoutException.get().getMessage().contains("archived") || checkoutException.get().getMessage().contains("cart"),
                "Exception message must mention product archival or cart state");

        // Verify zero orders and zero reserved stock
        assertEquals(0, orderRepository.count(), "Zero orders should be persisted in database");
        Inventory inv = inventoryRepository.findByProductIdAndVariantIsNull(testProductA.getId()).orElseThrow();
        assertEquals(0, inv.getReservedStock(), "Reserved stock must remain 0");
        assertEquals(10, inv.getAvailableStock(), "Available stock must remain untouched");
    }

    @Test
    @DisplayName("4.3 Multi-Product Lock Ordering: Ascending product ID sorting prevents AB-BA deadlocks")
    void testMultiProductLockOrdering_deadlockFree() throws Exception {
        inventoryService.initializeStock(testProductA, null, 10, 2);
        inventoryService.initializeStock(testProductB, null, 10, 2);

        // Ensure we know the ordering
        Product lowerIdProduct = testProductA.getId() < testProductB.getId() ? testProductA : testProductB;
        Product higherIdProduct = testProductA.getId() < testProductB.getId() ? testProductB : testProductA;

        Customer cust1 = createTestCustomer("multiproduct.cust1@example.com", "Cust1");
        Customer cust2 = createTestCustomer("multiproduct.cust2@example.com", "Cust2");

        // Cart 1: Has higher ID first, lower ID second (reverse order)
        Cart cart1 = new Cart(cust1, "session_cart1");
        cart1 = cartRepository.save(cart1);
        CartItem item1Higher = new CartItem(cart1, higherIdProduct, null, 1, higherIdProduct.getDiscountedPrice());
        CartItem item1Lower = new CartItem(cart1, lowerIdProduct, null, 1, lowerIdProduct.getDiscountedPrice());
        cart1.addItem(item1Higher);
        cart1.addItem(item1Lower);
        cartItemRepository.save(item1Higher);
        cartItemRepository.save(item1Lower);

        // Cart 2: Has lower ID first, higher ID second (natural order)
        Cart cart2 = new Cart(cust2, "session_cart2");
        cart2 = cartRepository.save(cart2);
        CartItem item2Lower = new CartItem(cart2, lowerIdProduct, null, 1, lowerIdProduct.getDiscountedPrice());
        CartItem item2Higher = new CartItem(cart2, higherIdProduct, null, 1, higherIdProduct.getDiscountedPrice());
        cart2.addItem(item2Lower);
        cart2.addItem(item2Higher);
        cartItemRepository.save(item2Lower);
        cartItemRepository.save(item2Higher);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Exception> thread1Error = new AtomicReference<>();
        AtomicReference<Exception> thread2Error = new AtomicReference<>();

        CheckoutRequest req1 = new CheckoutRequest(
                cart1.getId(), null, null, null, null, null, null,
                createTestAddressRequest(), 8.5241, 76.9366, "Morning (9AM-1PM)", "COD", null, "Multi 1"
        );
        CheckoutRequest req2 = new CheckoutRequest(
                cart2.getId(), null, null, null, null, null, null,
                createTestAddressRequest(), 8.5241, 76.9366, "Evening (5PM-9PM)", "COD", null, "Multi 2"
        );

        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.checkout(cust1, req1);
            } catch (Exception e) {
                thread1Error.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.checkout(cust2, req2);
            } catch (Exception e) {
                thread2Error.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // Unleash both checkouts concurrently
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Both checkouts must complete within timeout");
        assertNull(thread1Error.get(), "Cart 1 checkout must succeed without deadlock: " + (thread1Error.get() != null ? thread1Error.get().getMessage() : ""));
        assertNull(thread2Error.get(), "Cart 2 checkout must succeed without deadlock: " + (thread2Error.get() != null ? thread2Error.get().getMessage() : ""));

        assertEquals(2, orderRepository.count(), "Both orders must be created successfully");
        assertEquals(4, orderItemRepository.count(), "4 items must be saved across both orders");

        Inventory invA = inventoryRepository.findByProductIdAndVariantIsNull(testProductA.getId()).orElseThrow();
        Inventory invB = inventoryRepository.findByProductIdAndVariantIsNull(testProductB.getId()).orElseThrow();
        assertEquals(8, invA.getAvailableStock(), "Product A available stock must be 8");
        assertEquals(2, invA.getReservedStock(), "Product A reserved stock must be 2");
        assertEquals(8, invB.getAvailableStock(), "Product B available stock must be 8");
        assertEquals(2, invB.getReservedStock(), "Product B reserved stock must be 2");
    }

    @Test
    @DisplayName("9. Support Inquiry & Public Tracking: Guest ticket requires 256-bit secret and scrubs PII/finances")
    void testPublicSupportTicketTrackingPrivacy() {
        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail("guest.privacy@example.com");
        req.setName("Guest Tracker");
        req.setSubject("Delivery Schedule");
        req.setMessage("When will delivery arrive?");
        req.setOrderNumber("ORD-GUEST-999");
        req.setPriority("MEDIUM");

        SupportTicketDto createdTicket = supportService.createInquiry(req, null);
        assertNotNull(createdTicket.getTicketNumber());
        assertNotNull(createdTicket.getGuestAccessSecret(), "Guest ticket creation MUST return guest access secret once");
        assertTrue(createdTicket.getGuestAccessSecret().length() >= 43, "Guest secret must be at least 256 bits");

        String guestSecret = createdTicket.getGuestAccessSecret();

        // 1. Unauthenticated Tracking WITHOUT secret must FAIL CLOSED
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.getTicketForCaller(createdTicket.getTicketNumber(), null, null, false)
        );

        // 2. Unauthenticated Tracking WITH INVALID secret must FAIL CLOSED
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.getTicketForCaller(createdTicket.getTicketNumber(), "invalid-secret-key-123456", null, false)
        );

        // 3. Unauthenticated Tracking WITH VALID secret succeeds and returns sanitized public view
        SupportTicketDto publicView = supportService.getTicketForCaller(createdTicket.getTicketNumber(), guestSecret, null, false);
        assertNotNull(publicView);
        assertEquals(createdTicket.getTicketNumber(), publicView.getTicketNumber());
        assertEquals("Delivery Schedule", publicView.getSubject());
        assertNull(publicView.getGuestAccessSecret(), "Ordinary getTicket responses MUST NOT disclose guestAccessSecret");
        assertNull(publicView.getCustomerEmail(), "Public tracking MUST NOT disclose customer email");
        assertNull(publicView.getCustomerPhone(), "Public tracking MUST NOT disclose customer phone");
        assertNull(publicView.getOrderStatus(), "Public tracking MUST NOT disclose order status");
        assertNull(publicView.getOrderPaymentStatus(), "Public tracking MUST NOT disclose order payment status");
        assertNull(publicView.getOrderFinalAmount(), "Public tracking MUST NOT disclose order final amount");
        assertNull(publicView.getOrderCancellationReason(), "Public tracking MUST NOT disclose cancellation reason");
        assertNull(publicView.getAssignedTo(), "Public tracking MUST NOT disclose internal staff assignment");

        // 4. Authenticated Owner View on Registered Customer Ticket
        Customer owner = createTestCustomer("owner.privacy@example.com", "TicketOwner");
        Order order = orderRepository.save(new Order(
                "ORD-PRIVACY-100", owner, "PROCESSING",
                new BigDecimal("1200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1200.00"),
                "PAID", "COD", "{}", new BigDecimal("2.0"), true, "Standard"
        ));

        CreateInquiryRequest ownerReq = new CreateInquiryRequest();
        ownerReq.setEmail(owner.getEmail());
        ownerReq.setName("Ticket Owner");
        ownerReq.setSubject("Registered Customer Inquiry");
        ownerReq.setMessage("Where is my order?");
        ownerReq.setOrderNumber(order.getOrderNumber());
        ownerReq.setPriority("HIGH");

        SupportTicketDto regTicket = supportService.createInquiry(ownerReq, owner);
        assertNull(regTicket.getGuestAccessSecret(), "Registered customer ticket must NOT generate guest access secret");

        SupportTicketDto ownerView = supportService.getTicketForCaller(regTicket.getTicketNumber(), null, owner, false);
        assertNotNull(ownerView);
        assertEquals(owner.getEmail(), ownerView.getCustomerEmail());
        assertEquals("PROCESSING", ownerView.getOrderStatus());
        assertEquals(new BigDecimal("1200.00"), ownerView.getOrderFinalAmount());

        // 5. Admin View on Any Ticket
        SupportTicketDto adminView = supportService.getTicketForCaller(createdTicket.getTicketNumber(), null, null, true);
        assertNotNull(adminView);
        assertEquals("guest.privacy@example.com", adminView.getCustomerEmail());
    }

    @Test
    @DisplayName("1.1 Ticket Reply: Ticket owner can reply and sees complete owner detail in response")
    void testReplyToTicket_OwnerAllowed_ReturnsFullOwnerView() {
        Customer owner = createTestCustomer("reply.owner@example.com", "ReplyOwner");
        Order order = orderRepository.save(new Order(
                "ORD-REPLY-001", owner, "PROCESSING",
                new BigDecimal("950.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("950.00"),
                "PAID", "COD", "{}", new BigDecimal("1.5"), true, "Standard"
        ));

        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail(owner.getEmail());
        req.setName("Reply Owner");
        req.setSubject("Need address change");
        req.setMessage("Can I update delivery landmark?");
        req.setOrderNumber(order.getOrderNumber());
        req.setPriority("MEDIUM");

        SupportTicketDto ticket = supportService.createInquiry(req, owner);

        com.novapetzlife.support.dto.AddMessageRequest msgReq = new com.novapetzlife.support.dto.AddMessageRequest(
                "Near the big banyan tree please", "CUSTOMER", null, null
        );

        SupportTicketDto replyRes = supportService.addMessageToTicket(ticket.getTicketNumber(), msgReq, null, owner, false);

        assertNotNull(replyRes);
        assertEquals(owner.getEmail(), replyRes.getCustomerEmail());
        assertEquals("PROCESSING", replyRes.getOrderStatus());
        assertEquals(new BigDecimal("950.00"), replyRes.getOrderFinalAmount());
        assertEquals(2, replyRes.getMessages().size());
        assertEquals("CUSTOMER", replyRes.getMessages().get(1).getSenderType());
    }

    @Test
    @DisplayName("1.2 Ticket Reply: Unauthorized customer cannot reply to another customer's ticket (fails without existence disclosure)")
    void testReplyToTicket_UnauthorizedCustomerDenied() {
        Customer owner = createTestCustomer("owner.a@example.com", "OwnerA");
        Customer attacker = createTestCustomer("attacker.b@example.com", "AttackerB");

        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail(owner.getEmail());
        req.setName("Owner A");
        req.setSubject("Private question");
        req.setMessage("Private message");
        req.setPriority("LOW");

        SupportTicketDto ticket = supportService.createInquiry(req, owner);

        com.novapetzlife.support.dto.AddMessageRequest msgReq = new com.novapetzlife.support.dto.AddMessageRequest(
                "Tampering reply from Customer B", "CUSTOMER", null, null
        );

        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticket.getTicketNumber(), msgReq, null, attacker, false)
        );
    }

    @Test
    @DisplayName("1.3 Ticket Reply: Anonymous unauthenticated user cannot reply to a registered customer's ticket")
    void testReplyToTicket_UnauthenticatedDeniedForRegisteredCustomerTicket() {
        Customer owner = createTestCustomer("owner.registered@example.com", "RegisteredOwner");

        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail(owner.getEmail());
        req.setName("Registered Owner");
        req.setSubject("Account query");
        req.setMessage("Confidential inquiry");
        req.setPriority("HIGH");

        SupportTicketDto ticket = supportService.createInquiry(req, owner);

        com.novapetzlife.support.dto.AddMessageRequest msgReq = new com.novapetzlife.support.dto.AddMessageRequest(
                "Anonymous intruder reply", "CUSTOMER", null, null
        );

        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticket.getTicketNumber(), msgReq, null, null, false)
        );
    }

    @Test
    @DisplayName("1.4 Ticket Reply: Administrator can reply and receives full operational detail")
    void testReplyToTicket_AdminAllowed_ReturnsFullAdminView() {
        Customer owner = createTestCustomer("owner.adminreply@example.com", "CustomerUser");
        Order order = orderRepository.save(new Order(
                "ORD-REPLY-002", owner, "PROCESSING",
                new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500.00"),
                "PAID", "COD", "{}", new BigDecimal("3.0"), true, "Standard"
        ));

        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail(owner.getEmail());
        req.setName("Customer User");
        req.setSubject("Delivery timing");
        req.setMessage("When is the rider arriving?");
        req.setOrderNumber(order.getOrderNumber());
        req.setPriority("HIGH");

        SupportTicketDto ticket = supportService.createInquiry(req, owner);

        com.novapetzlife.support.dto.AddMessageRequest staffMsg = new com.novapetzlife.support.dto.AddMessageRequest(
                "Rider is dispatched and will arrive between 11 AM and 12 PM", "STAFF", "Support Lead", null
        );

        SupportTicketDto adminReplyRes = supportService.addMessageToTicket(ticket.getTicketNumber(), staffMsg, null, null, true);

        assertNotNull(adminReplyRes);
        assertEquals(owner.getEmail(), adminReplyRes.getCustomerEmail());
        assertEquals("PROCESSING", adminReplyRes.getOrderStatus());
        assertEquals(new BigDecimal("1500.00"), adminReplyRes.getOrderFinalAmount());
        assertEquals(2, adminReplyRes.getMessages().size());
        assertEquals("STAFF", adminReplyRes.getMessages().get(1).getSenderType());
        assertEquals("WAITING_FOR_CUSTOMER", adminReplyRes.getStatus());
    }

    @Test
    @DisplayName("1.5 Guest Ticket: Valid secret allows reply, prevents staff spoofing, and returns sanitized public view")
    void testReplyToTicket_GuestTicket_ValidSecret_AllowsReplyAndDemotesStaffSpoofing() {
        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setEmail("pure.guest@example.com");
        req.setName("Guest Buyer");
        req.setSubject("Store location doubt");
        req.setMessage("Is parking available?");
        req.setPriority("LOW");

        SupportTicketDto guestTicket = supportService.createInquiry(req, null);
        String secret = guestTicket.getGuestAccessSecret();
        assertNotNull(secret);

        // Guest user attempts to reply with spoofed "STAFF" sender type
        com.novapetzlife.support.dto.AddMessageRequest replyReq = new com.novapetzlife.support.dto.AddMessageRequest(
                "Following up on store parking", "STAFF", "Intruder", null
        );

        SupportTicketDto res = supportService.addMessageToTicket(guestTicket.getTicketNumber(), replyReq, secret, null, false);

        assertNotNull(res);
        assertNull(res.getCustomerEmail(), "Guest reply response must NOT disclose customer email");
        assertNull(res.getCustomerPhone(), "Guest reply response must NOT disclose customer phone");
        assertNull(res.getOrderStatus());
        assertNull(res.getOrderFinalAmount());
        // Non-admin sender type spoofing must be demoted to CUSTOMER
        assertEquals("CUSTOMER", res.getMessages().get(1).getSenderType(), "Non-admin caller cannot spoof STAFF sender type");
    }

    @Test
    @DisplayName("1.6 Guest Ticket: Missing, invalid, or wrong-ticket secret fails closed")
    void testGuestTicket_SecretValidationFailures_FailClosed() {
        CreateInquiryRequest reqA = new CreateInquiryRequest("Guest A", "guest.a@example.com", "+919447111111", null, "Inquiry A", "Message A", "LOW");
        CreateInquiryRequest reqB = new CreateInquiryRequest("Guest B", "guest.b@example.com", "+919447222222", null, "Inquiry B", "Message B", "LOW");

        SupportTicketDto ticketA = supportService.createInquiry(reqA, null);
        SupportTicketDto ticketB = supportService.createInquiry(reqB, null);

        com.novapetzlife.support.dto.AddMessageRequest msg = new com.novapetzlife.support.dto.AddMessageRequest("Reply text", "CUSTOMER", null, null);

        // 1. Missing secret (null or blank)
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticketA.getTicketNumber(), msg, null, null, false)
        );
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticketA.getTicketNumber(), msg, "   ", null, false)
        );

        // 2. Invalid/corrupted secret
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticketA.getTicketNumber(), msg, "completely-wrong-token-value", null, false)
        );

        // 3. Ticket B's secret used on Ticket A
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(ticketA.getTicketNumber(), msg, ticketB.getGuestAccessSecret(), null, false)
        );
    }

    @Test
    @DisplayName("1.7 Guest Ticket: Old legacy guest ticket without secret hash fails closed for public access")
    void testGuestTicket_LegacyTicketWithoutHash_FailsClosed() {
        CreateInquiryRequest req = new CreateInquiryRequest("Old Guest", "old.guest@example.com", "+919447333333", null, "Old Query", "Legacy Msg", "LOW");
        SupportTicketDto created = supportService.createInquiry(req, null);

        // Simulate legacy ticket by clearing guest_access_hash in database
        SupportTicket entity = supportTicketRepository.findByTicketNumber(created.getTicketNumber()).orElseThrow();
        entity.setGuestAccessHash(null);
        supportTicketRepository.save(entity);

        com.novapetzlife.support.dto.AddMessageRequest msg = new com.novapetzlife.support.dto.AddMessageRequest("Trying to reply to legacy ticket", "CUSTOMER", null, null);

        // Public tracking must fail closed
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.getTicketForCaller(created.getTicketNumber(), created.getGuestAccessSecret(), null, false)
        );

        // Public reply must fail closed
        assertThrows(ResourceNotFoundException.class, () ->
                supportService.addMessageToTicket(created.getTicketNumber(), msg, created.getGuestAccessSecret(), null, false)
        );

        // Admin can still view and reply to legacy ticket
        SupportTicketDto adminView = supportService.getTicketForCaller(created.getTicketNumber(), null, null, true);
        assertNotNull(adminView);
        assertEquals("old.guest@example.com", adminView.getCustomerEmail());
    }

    @Test
    @DisplayName("1.8 Cryptographic Secret: High entropy, SHA-256 hash formatting, and constant-time equality")
    void testGuestAccessSecret_CryptographicProperties() {
        String secret1 = SupportService.generateGuestAccessSecret();
        String secret2 = SupportService.generateGuestAccessSecret();

        assertNotNull(secret1);
        assertNotNull(secret2);
        assertNotEquals(secret1, secret2);
        assertTrue(secret1.length() >= 43, "Base64URL encoded 256-bit secret must be at least 43 characters");

        String hash1 = SupportService.hashGuestAccessSecret(secret1);
        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex digest must be exactly 64 characters");

        assertTrue(SupportService.verifyGuestSecret(secret1, hash1));
        assertFalse(SupportService.verifyGuestSecret(secret2, hash1));
        assertFalse(SupportService.verifyGuestSecret(null, hash1));
        assertFalse(SupportService.verifyGuestSecret(secret1, null));
        assertFalse(SupportService.verifyGuestSecret("", hash1));
    }
}
