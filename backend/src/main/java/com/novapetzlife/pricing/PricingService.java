package com.novapetzlife.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    public record PriceCalculationResult(
            BigDecimal purchaseCost,
            BigDecimal desiredMarginPercentage,
            BigDecimal suggestedSellingPrice,
            BigDecimal grossProfit
    ) {}

    public PriceCalculationResult calculateSuggestedPrice(BigDecimal purchaseCost, BigDecimal desiredMarginPercentage) {
        if (purchaseCost == null || purchaseCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase cost must be greater than zero");
        }
        if (desiredMarginPercentage == null || desiredMarginPercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Margin percentage must be non-negative");
        }

        BigDecimal marginMultiplier = BigDecimal.ONE.add(desiredMarginPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal suggestedPrice = purchaseCost.multiply(marginMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossProfit = suggestedPrice.subtract(purchaseCost);

        return new PriceCalculationResult(
                purchaseCost.setScale(2, RoundingMode.HALF_UP),
                desiredMarginPercentage.setScale(2, RoundingMode.HALF_UP),
                suggestedPrice,
                grossProfit
        );
    }

    public void validatePrice(BigDecimal basePrice, BigDecimal discountedPrice) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Base selling price must be greater than zero");
        }
        if (discountedPrice != null) {
            if (discountedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Discounted price must be greater than zero");
            }
            if (discountedPrice.compareTo(basePrice) > 0) {
                throw new IllegalArgumentException("Discounted price cannot exceed base selling price");
            }
        }
    }
}
