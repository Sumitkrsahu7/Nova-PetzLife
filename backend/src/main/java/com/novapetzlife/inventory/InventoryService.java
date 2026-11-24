package com.novapetzlife.inventory;

import com.novapetzlife.order.OrderItem;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryBatchRepository batchRepository;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final com.novapetzlife.audit.AuditService auditService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository transactionRepository,
            InventoryBatchRepository batchRepository,
            com.novapetzlife.notification.NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.audit.AuditService auditService) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public int getAvailableStock(Long productId, Long variantId) {
        Optional<Inventory> inv = variantId != null
                ? inventoryRepository.findByProductIdAndVariantId(productId, variantId)
                : inventoryRepository.findByProductIdAndVariantIsNull(productId);

        return inv.map(Inventory::getAvailableStock).orElse(0);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void initializeStock(Product product, ProductVariant variant, int initialStock, int minThreshold) {
        Inventory inventory = new Inventory(product, variant, initialStock, minThreshold);
        inventoryRepository.save(inventory);

        if (initialStock > 0) {
            transactionRepository.save(new InventoryTransaction(
                    product, variant, "INITIAL_STOCK", initialStock, "MANUAL", "INIT", "Initial product catalog stock"
            ));
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void reserveStock(Long productId, Long variantId, int quantity, String orderReference) {
        Inventory inventory = (variantId != null
                ? inventoryRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                : inventoryRepository.findByProductIdForUpdate(productId))
                .orElseThrow(() -> new IllegalStateException("No inventory record found for product: " + productId));

        inventory.reserve(quantity);
        inventoryRepository.save(inventory);

        transactionRepository.save(new InventoryTransaction(
                inventory.getProduct(), inventory.getVariant(), "RESERVED", quantity, "ORDER", orderReference, "Temporary checkout reservation"
        ));
    }

    public record InventoryKey(Long productId, Long variantId) implements Comparable<InventoryKey> {
        @Override
        public int compareTo(InventoryKey other) {
            int cmp = Long.compare(this.productId, other.productId);
            if (cmp != 0) return cmp;
            long v1 = this.variantId != null ? this.variantId : 0L;
            long v2 = other.variantId != null ? other.variantId : 0L;
            return Long.compare(v1, v2);
        }
    }

    /**
     * Atomically reserves stock for all items in an order with strict all-or-nothing semantics.
     * Aggregates requested quantities across duplicate order lines for the same product/variant,
     * and locks inventory rows deterministically by Product ID and Variant ID to avoid deadlocks.
     * If ANY item cannot be fulfilled, NO inventory rows are mutated and returns false.
     *
     * @param items List of order items to fulfill
     * @param orderReference Order reference number for audit logging
     * @return true if all items were successfully reserved; false if stock was insufficient
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean tryReserveAllItems(List<OrderItem> items, String orderReference) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // Aggregate requested quantities by (productId, variantId) to handle duplicate lines safely
        Map<InventoryKey, Integer> aggregatedQuantities = new HashMap<>();
        for (OrderItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            Long productId = item.getProduct().getId();
            Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;
            int qty = item.getQuantity();
            if (qty <= 0) continue;
            InventoryKey key = new InventoryKey(productId, variantId);
            aggregatedQuantities.merge(key, qty, Integer::sum);
        }

        if (aggregatedQuantities.isEmpty()) {
            return true;
        }

        // Sort aggregated keys deterministically by Product ID then Variant ID to guarantee deadlock-free lock ordering
        List<InventoryKey> sortedKeys = new ArrayList<>(aggregatedQuantities.keySet());
        Collections.sort(sortedKeys);

        // 1. Fetch and acquire pessimistic locks in deterministic order, verifying availability against aggregated demand
        Map<InventoryKey, Inventory> lockedInventories = new HashMap<>();
        for (InventoryKey key : sortedKeys) {
            int totalRequested = aggregatedQuantities.get(key);
            Inventory inventory = (key.variantId() != null
                    ? inventoryRepository.findByProductIdAndVariantIdForUpdate(key.productId(), key.variantId())
                    : inventoryRepository.findByProductIdForUpdate(key.productId()))
                    .orElse(null);

            if (inventory == null || inventory.getAvailableStock() < totalRequested) {
                log.warn("Cannot reserve stock for product {} (variant {}): aggregated requested {}, available {}",
                        key.productId(), key.variantId(), totalRequested, inventory != null ? inventory.getAvailableStock() : 0);
                return false; // All-or-nothing invariant: fail fast without mutating anything
            }
            lockedInventories.put(key, inventory);
        }

        // 2. All items verified: apply reservation exactly once per aggregated inventory key and persist audit transactions
        for (InventoryKey key : sortedKeys) {
            int totalQty = aggregatedQuantities.get(key);
            Inventory inventory = lockedInventories.get(key);
            inventory.reserve(totalQty);
            inventoryRepository.save(inventory);

            transactionRepository.save(new InventoryTransaction(
                    inventory.getProduct(), inventory.getVariant(), "RESERVED", totalQty, "ORDER", orderReference, "Atomic re-reservation for confirmed payment"
            ));
        }

        log.info("Successfully atomically reserved stock for {} inventory keys across {} order items on order {}",
                sortedKeys.size(), items.size(), orderReference);
        return true;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void releaseReservedStock(Long productId, Long variantId, int quantity, String orderReference, String reason) {
        Inventory inventory = (variantId != null
                ? inventoryRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                : inventoryRepository.findByProductIdForUpdate(productId))
                .orElseThrow(() -> new IllegalStateException("No inventory record found for product: " + productId));

        inventory.releaseReserved(quantity);
        inventoryRepository.save(inventory);

        transactionRepository.save(new InventoryTransaction(
                inventory.getProduct(), inventory.getVariant(), "RELEASED", quantity, "ORDER", orderReference, reason
        ));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deductConfirmedStock(Long productId, Long variantId, int quantity, String orderReference) {
        Inventory inventory = (variantId != null
                ? inventoryRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                : inventoryRepository.findByProductIdForUpdate(productId))
                .orElseThrow(() -> new IllegalStateException("No inventory record found for product: " + productId));

        inventory.deductConfirmed(quantity);
        inventoryRepository.save(inventory);

        transactionRepository.save(new InventoryTransaction(
                inventory.getProduct(), inventory.getVariant(), "DEDUCTED", quantity, "ORDER", orderReference, "Confirmed order fulfillment deduction"
        ));

        // Low stock threshold automated owner alert
        if (inventory.getAvailableStock() <= inventory.getLowStockThreshold()) {
            notificationService.notifyShopOwnerLowStock(
                    inventory.getProduct(),
                    inventory.getAvailableStock(),
                    inventory.getLowStockThreshold()
            );
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void restockReturnedItem(Long productId, Long variantId, int quantity, String returnReference, String reason) {
        Inventory inventory = (variantId != null
                ? inventoryRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                : inventoryRepository.findByProductIdForUpdate(productId))
                .orElseThrow(() -> new IllegalStateException("No inventory record found for product: " + productId));

        inventory.restock(quantity);
        inventoryRepository.save(inventory);

        transactionRepository.save(new InventoryTransaction(
                inventory.getProduct(), inventory.getVariant(), "RESTOCK", quantity, "RETURN", returnReference,
                reason != null ? reason : "Returned item restocked after store inspection"
        ));
    }

    @Transactional
    public void adjustStock(Long productId, Long variantId, int quantityAdjustment, String reason) {
        Inventory inventory = (variantId != null
                ? inventoryRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                : inventoryRepository.findByProductIdForUpdate(productId))
                .orElseThrow(() -> new IllegalStateException("No inventory record found for product: " + productId));

        if (quantityAdjustment > 0) {
            inventory.restock(quantityAdjustment);
        } else if (quantityAdjustment < 0) {
            int toDeduct = Math.abs(quantityAdjustment);
            inventory.deductConfirmed(toDeduct);
        }

        inventoryRepository.save(inventory);

        transactionRepository.save(new InventoryTransaction(
                inventory.getProduct(), inventory.getVariant(), "ADJUSTMENT", quantityAdjustment, "MANUAL", "ADMIN_ACTION", reason
        ));

        if (auditService != null) {
            auditService.recordAudit("Inventory", String.valueOf(inventory.getId()), "STOCK_ADJUSTMENT", "ADMIN", null, "Adjustment: " + quantityAdjustment + ", Reason: " + reason, null);
        }
    }

    @Transactional(readOnly = true)
    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Inventory> getLowStockAlerts() {
        return inventoryRepository.findLowStockItems();
    }

    @Transactional(readOnly = true)
    public List<Inventory> getInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<InventoryBatch> getNearExpiryBatches(int daysThreshold) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(daysThreshold);
        return batchRepository.findByExpiryDateBetweenAndIsActiveTrue(today, threshold);
    }
}
