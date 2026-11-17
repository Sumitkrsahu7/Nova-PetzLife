package com.novapetzlife.analytics;

import com.novapetzlife.analytics.dto.AnalyticsSummaryDto;
import com.novapetzlife.analytics.dto.LowStockAlertDto;
import com.novapetzlife.analytics.dto.TopProductDto;
import com.novapetzlife.inventory.Inventory;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.promotion.CouponRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final RefundRepository refundRepository;
    private final CouponRepository couponRepository;

    public AnalyticsService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryRepository inventoryRepository,
            RefundRepository refundRepository,
            CouponRepository couponRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.refundRepository = refundRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getAnalyticsSummary() {
        List<Order> allOrders = orderRepository.findAll();

        long totalOrders = allOrders.size();
        long deliveredOrders = allOrders.stream().filter(o -> "DELIVERED".equalsIgnoreCase(o.getOrderStatus())).count();
        long cancelledOrders = allOrders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getOrderStatus())).count();
        long within20km = allOrders.stream().filter(o -> Boolean.TRUE.equals(o.getIsWithinRadius())).count();

        // Valid orders for revenue (exclude cancelled)
        List<Order> validOrders = allOrders.stream()
                .filter(o -> !"CANCELLED".equalsIgnoreCase(o.getOrderStatus()))
                .toList();

        BigDecimal totalRevenue = validOrders.stream()
                .map(Order::getFinalPayableAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal aov = validOrders.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(validOrders.size()), 2, RoundingMode.HALF_UP);

        BigDecimal totalRefunded = refundRepository.findAll().stream()
                .map(Refund::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeCouponsCount = couponRepository.findByIsActiveTrue().size();

        long lowStockCount = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getAvailableStock() <= inv.getLowStockThreshold())
                .count();

        Map<String, Long> statusMap = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getOrderStatus, Collectors.counting()));

        Map<String, Long> paymentMap = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getPaymentMethod, Collectors.counting()));

        return new AnalyticsSummaryDto(
                totalRevenue,
                totalOrders,
                deliveredOrders,
                cancelledOrders,
                aov,
                totalRefunded,
                activeCouponsCount,
                lowStockCount,
                within20km,
                statusMap,
                paymentMap
        );
    }

    @Transactional(readOnly = true)
    public List<LowStockAlertDto> getLowStockAlerts() {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getAvailableStock() <= inv.getLowStockThreshold())
                .map(inv -> {
                    Product p = inv.getProduct();
                    int diff = inv.getLowStockThreshold() - inv.getAvailableStock() + 10;
                    return new LowStockAlertDto(
                            p.getId(),
                            p.getName(),
                            p.getSku(),
                            p.getCategory() != null ? p.getCategory().getName() : "General",
                            inv.getAvailableStock(),
                            inv.getLowStockThreshold(),
                            diff
                    );
                })
                .sorted(Comparator.comparingInt(LowStockAlertDto::getCurrentStock))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopProductDto> getTopSellingProducts(int limit) {
        List<OrderItem> items = orderItemRepository.findAll();

        Map<Long, List<OrderItem>> groupedByProduct = items.stream()
                .filter(it -> it.getProduct() != null)
                .collect(Collectors.groupingBy(it -> it.getProduct().getId()));

        return groupedByProduct.entrySet().stream()
                .map(entry -> {
                    Long prodId = entry.getKey();
                    List<OrderItem> orderItems = entry.getValue();
                    String prodName = orderItems.get(0).getProductName();
                    String brandName = orderItems.get(0).getBrandName();
                    long qty = orderItems.stream().mapToLong(OrderItem::getQuantity).sum();
                    BigDecimal revenue = orderItems.stream()
                            .map(OrderItem::getTotalPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new TopProductDto(prodId, prodName, brandName, qty, revenue);
                })
                .sorted((a, b) -> Long.compare(b.getTotalQuantitySold(), a.getTotalQuantitySold()))
                .limit(limit)
                .toList();
    }
}
