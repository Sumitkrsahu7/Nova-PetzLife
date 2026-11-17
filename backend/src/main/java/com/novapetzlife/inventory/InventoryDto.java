package com.novapetzlife.inventory;

public record InventoryDto(
        Long id,
        Long productId,
        String productName,
        String sku,
        String brandName,
        Long variantId,
        String variantName,
        Integer availableStock,
        Integer reservedStock,
        Integer totalStock,
        Integer minStockThreshold,
        Boolean isLowStock
) {
    public static InventoryDto fromEntity(Inventory inv) {
        String sku = null;
        String brandName = null;
        if (inv.getProduct() != null) {
            sku = inv.getProduct().getSku();
            if (inv.getProduct().getBrand() != null) {
                brandName = inv.getProduct().getBrand().getName();
            }
        }
        if (inv.getVariant() != null && inv.getVariant().getSku() != null) {
            sku = inv.getVariant().getSku();
        }

        return new InventoryDto(
                inv.getId(),
                inv.getProduct() != null ? inv.getProduct().getId() : null,
                inv.getProduct() != null ? inv.getProduct().getName() : null,
                sku != null ? sku : "SKU-" + inv.getId(),
                brandName != null ? brandName : "Nova Petzlife",
                inv.getVariant() != null ? inv.getVariant().getId() : null,
                inv.getVariant() != null ? inv.getVariant().getVariantName() : null,
                inv.getAvailableStock(),
                inv.getReservedStock(),
                inv.getCurrentStock(),
                inv.getMinStockThreshold(),
                inv.isLowStock()
        );
    }
}
