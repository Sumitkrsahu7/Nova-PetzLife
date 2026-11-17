package com.novapetzlife.inventory;

import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductVariant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

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
