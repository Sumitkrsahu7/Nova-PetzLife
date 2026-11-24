package com.novapetzlife.inventory;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private com.novapetzlife.support.SupportTicketRepository supportTicketRepository;

    @Autowired(required = false)
    private com.novapetzlife.support.SupportMessageRepository supportMessageRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        if (supportMessageRepository != null) supportMessageRepository.deleteAll();
        if (supportTicketRepository != null) supportTicketRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Brand brand = brandRepository.save(new Brand("Farmina N&D", "farmina-" + suffix, "Italian grain-free pet food"));
        Category category = categoryRepository.save(new Category("Cat Food", "cat-food-" + suffix, "Feline nutrition", null, 1));

        testProduct = new Product(
                brand,
                category,
                "Farmina Ocean Herring & Orange Cat Food",
                "farmina-ocean-herring-" + suffix,
                "FM-CAT-" + suffix.toUpperCase(),
                new BigDecimal("1550.00"),
                new BigDecimal("1399.00")
        );
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("Stock Lifecycle: Stock initialization, reservation, release, and order deduction")
    void testStockLifecycle() {
        // 1. Initialize 20 units
        inventoryService.initializeStock(testProduct, null, 20, 5);
        assertEquals(20, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 2. Reserve 5 units for an in-flight checkout
        inventoryService.reserveStock(testProduct.getId(), null, 5, "ORD-1001");
        assertEquals(15, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 3. Release 2 units (cart expired or user removed item)
        inventoryService.releaseReservedStock(testProduct.getId(), null, 2, "ORD-1001", "User reduced cart quantity");
        assertEquals(17, inventoryService.getAvailableStock(testProduct.getId(), null));

        // 4. Confirm remaining 3 units upon payment success
        inventoryService.deductConfirmedStock(testProduct.getId(), null, 3, "ORD-1001");
        assertEquals(17, inventoryService.getAvailableStock(testProduct.getId(), null));

        Inventory inventory = inventoryRepository.findByProductIdAndVariantIsNull(testProduct.getId()).orElseThrow();
        assertEquals(17, inventory.getCurrentStock());
        assertEquals(17, inventory.getAvailableStock());
        assertEquals(0, inventory.getReservedStock());
    }

    @Test
    @DisplayName("Pessimistic Concurrency: Prevent overselling under high concurrent checkout demand")
    void testConcurrentReservationsPreventOverselling() throws InterruptedException {
        int initialStock = 10;
        int totalAttemptingUsers = 20;
        int quantityPerReservation = 1;

        inventoryService.initializeStock(testProduct, null, initialStock, 2);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch readyLatch = new CountDownLatch(totalAttemptingUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalAttemptingUsers);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger failedReservations = new AtomicInteger(0);

        for (int i = 0; i < totalAttemptingUsers; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Fire simultaneously
                    boolean reserved = false;
                    for (int attempt = 0; attempt < 5 && !reserved; attempt++) {
                        try {
                            inventoryService.reserveStock(
                                    testProduct.getId(),
                                    null,
                                    quantityPerReservation,
                                    "CONCURRENT-ORDER-" + userId
                            );
                            successfulReservations.incrementAndGet();
                            reserved = true;
                        } catch (IllegalStateException e) {
                            // Out of stock exception caught
                            failedReservations.incrementAndGet();
                            break;
                        } catch (Exception e) {
                            // Transient database lock contention, short backoff and retry
                            Thread.sleep(50);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Unleash threads
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(finished, "All concurrent tasks should complete within timeout");
        assertEquals(10, successfulReservations.get(), "Exactly 10 reservations must succeed for 10 units in stock");
        assertEquals(10, failedReservations.get(), "Excess 10 reservations must fail due to zero available stock");

        int remainingAvailable = inventoryService.getAvailableStock(testProduct.getId(), null);
        assertEquals(0, remainingAvailable, "Available stock must be exactly 0 after all units are reserved");

        Inventory inventory = inventoryRepository.findByProductIdAndVariantIsNull(testProduct.getId()).orElseThrow();
        assertEquals(10, inventory.getReservedStock());
        assertEquals(10, inventory.getCurrentStock());
    }

    @Test
    @DisplayName("Low Stock Detection: Low stock alerts trigger when available stock drops below threshold")
    void testLowStockDetectionAndRestock() {
        // Initialize with 4 units (threshold = 5) -> triggers low stock alert
        inventoryService.initializeStock(testProduct, null, 4, 5);

        List<Inventory> alerts = inventoryService.getLowStockAlerts();
        boolean hasTestProduct = alerts.stream().anyMatch(inv -> inv.getProduct().getId().equals(testProduct.getId()));
        assertTrue(hasTestProduct, "Product with 4 units and min threshold 5 must be in low stock list");

        // Restock by +10 units
        inventoryService.adjustStock(testProduct.getId(), null, 10, "Warehouse restock batch");

        assertEquals(14, inventoryService.getAvailableStock(testProduct.getId(), null));
        List<Inventory> updatedAlerts = inventoryService.getLowStockAlerts();
        boolean stillLow = updatedAlerts.stream().anyMatch(inv -> inv.getProduct().getId().equals(testProduct.getId()));
        assertFalse(stillLow, "Product should no longer be reported as low stock after restock");
    }

    @Test
    @DisplayName("Duplicate Lines: Combined quantity within available stock aggregates and reserves correctly")
    void testDuplicateLines_CombinedWithinAvailableStock_SucceedsAndBalancesCorrect() {
        inventoryService.initializeStock(testProduct, null, 10, 2);
        assertEquals(10, inventoryService.getAvailableStock(testProduct.getId(), null));

        Order order = new Order();
        order.setOrderNumber("ORD-DUP-1");
        OrderItem item1 = new OrderItem(order, testProduct, null, testProduct.getName(), "Farmina", testProduct.getSku(), 3,
                new BigDecimal("1399.00"), new BigDecimal("4197.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");
        OrderItem item2 = new OrderItem(order, testProduct, null, testProduct.getName(), "Farmina", testProduct.getSku(), 4,
                new BigDecimal("1399.00"), new BigDecimal("5596.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");

        boolean result = inventoryService.tryReserveAllItems(List.of(item1, item2), order.getOrderNumber());
        assertTrue(result, "Aggregated reservation of 3 + 4 = 7 units out of 10 must succeed");

        assertEquals(3, inventoryService.getAvailableStock(testProduct.getId(), null));
        Inventory inv = inventoryRepository.findByProductIdAndVariantIsNull(testProduct.getId()).orElseThrow();
        assertEquals(7, inv.getReservedStock());
        assertEquals(10, inv.getCurrentStock());

        List<InventoryTransaction> txs = inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        long reservedTxCount = txs.stream().filter(t -> "RESERVED".equalsIgnoreCase(t.getTransactionType())).count();
        assertEquals(1, reservedTxCount, "Exactly 1 aggregated reservation transaction must be recorded per inventory key");
        InventoryTransaction tx = txs.stream().filter(t -> "RESERVED".equalsIgnoreCase(t.getTransactionType())).findFirst().orElseThrow();
        assertEquals(7, tx.getQuantity());
        assertEquals(order.getOrderNumber(), tx.getReferenceId());
    }

    @Test
    @DisplayName("Duplicate Lines: Combined quantity exceeding available stock fails all-or-nothing without balance mutation")
    void testDuplicateLines_CombinedExceedsAvailableStock_FailsAllOrNothing() {
        inventoryService.initializeStock(testProduct, null, 6, 2);
        assertEquals(6, inventoryService.getAvailableStock(testProduct.getId(), null));

        Order order = new Order();
        order.setOrderNumber("ORD-DUP-2");
        OrderItem item1 = new OrderItem(order, testProduct, null, testProduct.getName(), "Farmina", testProduct.getSku(), 4,
                new BigDecimal("1399.00"), new BigDecimal("5596.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");
        OrderItem item2 = new OrderItem(order, testProduct, null, testProduct.getName(), "Farmina", testProduct.getSku(), 3,
                new BigDecimal("1399.00"), new BigDecimal("4197.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");

        // 4 + 3 = 7, but stock is only 6
        boolean result = inventoryService.tryReserveAllItems(List.of(item1, item2), order.getOrderNumber());
        assertFalse(result, "Aggregated demand 7 > stock 6 must fail all-or-nothing");

        assertEquals(6, inventoryService.getAvailableStock(testProduct.getId(), null), "Available stock must remain untouched");
        Inventory inv = inventoryRepository.findByProductIdAndVariantIsNull(testProduct.getId()).orElseThrow();
        assertEquals(0, inv.getReservedStock(), "Reserved stock must remain zero");
        assertEquals(6, inv.getCurrentStock());

        List<InventoryTransaction> txs = inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        long reservedTxCount = txs.stream().filter(t -> "RESERVED".equalsIgnoreCase(t.getTransactionType())).count();
        assertEquals(0, reservedTxCount, "Zero reservation transactions must be created on failed all-or-nothing reservation");
    }

    @Test
    @DisplayName("Multiple Variants: Deterministic sorted lock order and exact variant inventory deductions")
    void testMultipleVariants_DeterministicOrderAndStockBalances() {
        // Create 2 variants for testProduct
        ProductVariant var1 = new ProductVariant();
        var1.setProduct(testProduct);
        var1.setSku("VAR-1-KG-" + UUID.randomUUID().toString().substring(0, 5));
        var1.setVariantName("1 kg Pouch");
        var1.setPrice(new BigDecimal("800.00"));
        var1.setIsActive(true);

        ProductVariant var2 = new ProductVariant();
        var2.setProduct(testProduct);
        var2.setSku("VAR-5-KG-" + UUID.randomUUID().toString().substring(0, 5));
        var2.setVariantName("5 kg Bag");
        var2.setPrice(new BigDecimal("3500.00"));
        var2.setIsActive(true);

        testProduct.getVariants().add(var1);
        testProduct.getVariants().add(var2);
        testProduct = productRepository.save(testProduct);

        ProductVariant savedVar1 = testProduct.getVariants().stream().filter(v -> v.getSku().startsWith("VAR-1-KG")).findFirst().orElseThrow();
        ProductVariant savedVar2 = testProduct.getVariants().stream().filter(v -> v.getSku().startsWith("VAR-5-KG")).findFirst().orElseThrow();

        inventoryService.initializeStock(testProduct, savedVar1, 5, 1);
        inventoryService.initializeStock(testProduct, savedVar2, 5, 1);

        Order order = new Order();
        order.setOrderNumber("ORD-VAR-1");
        // Pass in reverse order (var2 then var1) to ensure deterministic sorting works
        OrderItem itemVar2 = new OrderItem(order, testProduct, savedVar2, testProduct.getName(), "Farmina", savedVar2.getSku(), 2,
                savedVar2.getPrice(), new BigDecimal("7000.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");
        OrderItem itemVar1 = new OrderItem(order, testProduct, savedVar1, testProduct.getName(), "Farmina", savedVar1.getSku(), 3,
                savedVar1.getPrice(), new BigDecimal("2400.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");

        boolean result = inventoryService.tryReserveAllItems(List.of(itemVar2, itemVar1), order.getOrderNumber());
        assertTrue(result, "Both variants must be reserved successfully");

        assertEquals(2, inventoryService.getAvailableStock(testProduct.getId(), savedVar1.getId()), "Var1: 5 - 3 = 2 available");
        assertEquals(3, inventoryService.getAvailableStock(testProduct.getId(), savedVar2.getId()), "Var2: 5 - 2 = 3 available");
    }

    @Test
    @DisplayName("Concurrent Reservations: Overlapping items across threads avoid deadlocks and conserve balances")
    void testConcurrentReservations_OverlappingItemsConserveBalances() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product productB = productRepository.save(new Product(
                testProduct.getBrand(), testProduct.getCategory(), "Product B", "prod-b-" + suffix, "SKU-B-" + suffix,
                new BigDecimal("500.00"), new BigDecimal("500.00")
        ));

        // 10 units for product A, 10 units for product B
        inventoryService.initializeStock(testProduct, null, 10, 1);
        inventoryService.initializeStock(productB, null, 10, 1);

        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        AtomicInteger successfulOrders = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Order order = new Order();
                    order.setOrderNumber("ORD-CONC-" + index);
                    // Odd threads pass (A, B); Even threads pass (B, A) in reverse order to test deadlock freedom
                    OrderItem itemA = new OrderItem(order, testProduct, null, testProduct.getName(), "Brand", testProduct.getSku(), 1,
                            new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");
                    OrderItem itemB = new OrderItem(order, productB, null, productB.getName(), "Brand", productB.getSku(), 1,
                            new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2309", "{}", "{}");
                    List<OrderItem> list = (index % 2 == 0) ? List.of(itemA, itemB) : List.of(itemB, itemA);

                    boolean reserved = false;
                    for (int attempt = 0; attempt < 10 && !reserved; attempt++) {
                        try {
                            if (inventoryService.tryReserveAllItems(list, order.getOrderNumber())) {
                                successfulOrders.incrementAndGet();
                                reserved = true;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            try {
                                Thread.sleep(60);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent overlapping reservations completed without deadlock");
        assertEquals(10, successfulOrders.get(), "All 10 orders reserving 1 unit of A and B should succeed");
        assertEquals(0, inventoryService.getAvailableStock(testProduct.getId(), null));
        assertEquals(0, inventoryService.getAvailableStock(productB.getId(), null));
    }
}
