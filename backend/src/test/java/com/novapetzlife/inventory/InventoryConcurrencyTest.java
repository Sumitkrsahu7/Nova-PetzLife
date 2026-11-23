package com.novapetzlife.inventory;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
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
}
