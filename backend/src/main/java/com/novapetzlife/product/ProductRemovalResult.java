package com.novapetzlife.product;

public record ProductRemovalResult(
        Long productId,
        String productName,
        String sku,
        String action, // "ARCHIVED" or "DELETED"
        String message
) {}
