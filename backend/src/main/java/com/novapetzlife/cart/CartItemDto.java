package com.novapetzlife.cart;

import com.novapetzlife.product.ProductImage;

import java.math.BigDecimal;

public record CartItemDto(
        Long id,
        Long productId,
        String productName,
        String productSlug,
        String brandName,
        Long variantId,
        String variantName,
        String primaryImageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Integer availableStock,
        Boolean returnAllowed,
        Integer returnWindowDays
) {
    public static CartItemDto fromEntity(CartItem item, Integer availableStock) {
        String imgUrl = item.getProduct().getImages() != null && !item.getProduct().getImages().isEmpty()
                ? item.getProduct().getImages().stream().filter(ProductImage::getIsPrimary).findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(item.getProduct().getImages().get(0).getImageUrl())
                : null;

        boolean returnAllowed = item.getProduct().getReturnPolicy() != null
                && Boolean.TRUE.equals(item.getProduct().getReturnPolicy().getReturnAllowed());
        int returnWindow = item.getProduct().getReturnPolicy() != null
                ? item.getProduct().getReturnPolicy().getReturnWindowDays()
                : 0;

        return new CartItemDto(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSlug(),
                item.getProduct().getBrand() != null ? item.getProduct().getBrand().getName() : null,
                item.getVariant() != null ? item.getVariant().getId() : null,
                item.getVariant() != null ? item.getVariant().getVariantName() : null,
                imgUrl,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                availableStock != null ? availableStock : 0,
                returnAllowed,
                returnWindow
        );
    }
}
