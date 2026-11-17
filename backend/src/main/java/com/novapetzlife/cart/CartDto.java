package com.novapetzlife.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        Long id,
        String sessionToken,
        List<CartItemDto> items,
        Integer totalItems,
        BigDecimal subtotal,
        String currency
) {
    public static CartDto of(Long cartId, String sessionToken, List<CartItemDto> items) {
        int totalItems = items.stream().mapToInt(CartItemDto::quantity).sum();
        BigDecimal subtotal = items.stream()
                .map(CartItemDto::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDto(cartId, sessionToken, items, totalItems, subtotal, "INR");
    }
}
