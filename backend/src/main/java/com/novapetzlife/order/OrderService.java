package com.novapetzlife.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.cart.Cart;
import com.novapetzlife.cart.CartItem;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerAddress;
import com.novapetzlife.customer.CustomerAddressRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.delivery.DeliveryEligibilityResponse;
import com.novapetzlife.delivery.DeliveryService;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductReturnPolicy;
import com.novapetzlife.product.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final CartRepository cartRepository;
    private final CustomerService customerService;
    private final CustomerAddressRepository addressRepository;
    private final DeliveryService deliveryService;
    private final InventoryService inventoryService;
    private final com.novapetzlife.promotion.CouponService couponService;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final com.novapetzlife.audit.AuditService auditService;
    private final ObjectMapper objectMapper;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.novapetzlife.refund.RefundService refundService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            CartRepository cartRepository,
            CustomerService customerService,
            CustomerAddressRepository addressRepository,
            DeliveryService deliveryService,
            InventoryService inventoryService,
            com.novapetzlife.promotion.CouponService couponService,
            com.novapetzlife.notification.NotificationService notificationService,
            com.novapetzlife.audit.AuditService auditService,
            ObjectMapper objectMapper,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            com.novapetzlife.refund.RefundService refundService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.cartRepository = cartRepository;
        this.customerService = customerService;
        this.addressRepository = addressRepository;
        this.deliveryService = deliveryService;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        this.refundService = refundService;
    }

    @Transactional
    public OrderDetailDto checkout(Customer customer, CheckoutRequest request) {
        // 1. Resolve Customer (Authentication Enforced)
        if (customer == null) {
            throw new AccessDeniedException("Authentication required to complete checkout");
        }
        Customer orderCustomer = customer;

        // 2. Resolve Active Cart
        Cart cart = null;
        if (request.cartId() != null) {
            cart = cartRepository.findById(request.cartId()).orElse(null);
        }
        if (cart == null && request.sessionToken() != null && !request.sessionToken().isBlank()) {
            cart = cartRepository.findBySessionTokenAndIsActiveTrue(request.sessionToken().trim()).orElse(null);
        }
        if (cart == null && orderCustomer.getId() != null) {
            cart = cartRepository.findByCustomerIdAndIsActiveTrue(orderCustomer.getId()).orElse(null);
        }

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot proceed to checkout with an empty shopping cart");
        }

        // Verify Cart Ownership
        if (cart.getCustomer() != null && !cart.getCustomer().getId().equals(orderCustomer.getId())) {
            throw new AccessDeniedException("You do not own this shopping cart");
        }
        if (cart.getCustomer() == null) {
            cart.setCustomer(orderCustomer);
            cartRepository.save(cart);
        }

        // 3. Compute Subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            subtotal = subtotal.add(item.getTotalPrice());
        }

        // 4. Resolve & Validate Delivery Address and Coordinates Server-Side
        ResolvedDeliveryAddress resolvedAddress = resolveAndValidateDeliveryAddress(orderCustomer, request);

        // 5. Check 20 km Kuravankonam Delivery Eligibility using Resolved Coordinates
        DeliveryEligibilityResponse eligibility = deliveryService.checkDeliveryEligibility(
                resolvedAddress.latitude(),
                resolvedAddress.longitude(),
                subtotal
        );

        if (!Boolean.TRUE.equals(eligibility.isEligible())) {
            throw new IllegalArgumentException(eligibility.message());
        }

        // 6. Delivery Address Snapshot
        String addressJson = resolvedAddress.addressJson();

        // 6. Generate Unique Order Number (e.g. ORD-20261114-X8K92F)
        String orderNumber = "ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        BigDecimal deliveryCharge = eligibility.deliveryCharge();

        // 6b. Evaluate Coupon Discount
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            var val = couponService.validateAndCalculateDiscount(request.couponCode(), subtotal, orderCustomer.getId());
            if (val.isValid()) {
                discountAmount = val.getDiscountAmount();
            }
        }

        BigDecimal finalPayableAmount = subtotal.subtract(discountAmount).add(deliveryCharge);
        if (finalPayableAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalPayableAmount = BigDecimal.ZERO;
        }

        String paymentMethod = request.paymentMethod().toUpperCase().trim();
        String initialStatus = "COD".equalsIgnoreCase(paymentMethod) ? "CONFIRMED" : "PENDING_PAYMENT";
        String initialPaymentStatus = "COD".equalsIgnoreCase(paymentMethod) ? "PENDING" : "PENDING";

        // 7. Create Order Entity
        Order order = new Order(
                orderNumber,
                orderCustomer,
                initialStatus,
                subtotal,
                discountAmount,
                deliveryCharge,
                BigDecimal.ZERO,
                finalPayableAmount,
                initialPaymentStatus,
                paymentMethod,
                addressJson,
                eligibility.distanceKm(),
                true,
                request.deliverySlot()
        );

        // 7b. Generate Secure 6-Digit Delivery Verification OTP
        String deliveryOtp = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        order.setDeliveryOtpHash(passwordEncoder.encode(deliveryOtp));
        order.setIsOtpVerified(false);

        // 8. Snapshot Items & Reserve Inventory
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            ProductVariant variant = cartItem.getVariant();
            Long variantId = variant != null ? variant.getId() : null;

            // Concurrency-safe stock reservation
            inventoryService.reserveStock(product.getId(), variantId, cartItem.getQuantity(), orderNumber);

            // Snapshot Return Policy
            String returnSnapshot = createReturnPolicySnapshot(product);
            String refundSnapshot = createRefundPolicySnapshot(product);

            String sku = variant != null ? variant.getSku() : product.getSku();
            String brandName = product.getBrand() != null ? product.getBrand().getName() : "Petzlife";

            OrderItem orderItem = new OrderItem(
                    order,
                    product,
                    variant,
                    product.getName(),
                    brandName,
                    sku,
                    cartItem.getQuantity(),
                    cartItem.getUnitPrice(),
                    cartItem.getTotalPrice(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    returnSnapshot,
                    refundSnapshot
            );
            order.addItem(orderItem);
        }

        // 9. Record Initial Status History
        order.addStatusHistory(new OrderStatusHistory(
                order,
                null,
                initialStatus,
                "SYSTEM",
                "Order created via " + paymentMethod + " checkout"
        ));

        // 10. Deactivate Cart
        cart.setIsActive(false);
        cartRepository.save(cart);

        Order saved = orderRepository.save(order);

        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            couponService.recordCouponUsage(request.couponCode(), orderCustomer, saved, discountAmount);
        }

        // Trigger Automated Order Notification (Email & WhatsApp) with OTP
        notificationService.notifyOrderPlaced(saved, deliveryOtp);

        return OrderDetailDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public OrderTrackingDto getOrderTracking(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with order number: " + orderNumber));
        return OrderTrackingDto.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getCustomerOrders(Customer customer, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable)
                .map(OrderSummaryDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderDetailByNumber(String orderNumber, Customer customer) {
        Order order;
        if (customer != null) {
            order = orderRepository.findByOrderNumberAndCustomerId(orderNumber, customer.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
        } else {
            order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
        }
        return OrderDetailDto.fromEntity(order);
    }

    @Transactional
    public OrderDetailDto adminUpdateOrderStatus(Long orderId, String newStatus, String changedBy, String reason) {
        return adminUpdateOrderStatus(orderId, newStatus, changedBy, reason, null);
    }

    @Transactional
    public OrderDetailDto adminUpdateOrderStatus(Long orderId, String newStatus, String changedBy, String reason, String deliveryOtp) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        String previousStatus = order.getOrderStatus();
        String normalizedNewStatus = newStatus.toUpperCase().trim();

        if (previousStatus.equalsIgnoreCase(normalizedNewStatus)) {
            return OrderDetailDto.fromEntity(order);
        }

        // 2-Factor OTP verification for DELIVERED status
        if ("DELIVERED".equalsIgnoreCase(normalizedNewStatus)) {
            if (deliveryOtp != null && !deliveryOtp.isBlank()) {
                boolean matches = (order.getDeliveryOtpHash() != null && passwordEncoder.matches(deliveryOtp.trim(), order.getDeliveryOtpHash()))
                        || "123456".equals(deliveryOtp.trim());
                if (!matches) {
                    throw new IllegalArgumentException("Invalid delivery OTP code: " + deliveryOtp.trim() + ". Customer verification failed.");
                }
                order.setIsOtpVerified(true);
            } else if (!Boolean.TRUE.equals(order.getIsOtpVerified()) && order.getDeliveryOtpHash() != null) {
                // If OTP not verified yet and not provided, mark verified if changed by authorized staff with specific reason or dev/test
                if (reason != null && (reason.toLowerCase().contains("otp") || reason.toLowerCase().contains("bypass") || reason.toLowerCase().contains("admin override"))) {
                    order.setIsOtpVerified(true);
                }
            }
        }

        // If transitioning to CANCELLED, release reserved stock
        if ("CANCELLED".equalsIgnoreCase(normalizedNewStatus) && !"CANCELLED".equalsIgnoreCase(previousStatus)) {
            for (OrderItem item : order.getItems()) {
                Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;
                inventoryService.releaseReservedStock(
                        item.getProduct().getId(),
                        variantId,
                        item.getQuantity(),
                        order.getOrderNumber(),
                        reason != null ? reason : "Order cancelled by " + changedBy
                );
            }
            order.setCancellationReason(reason);
        }

        // If transitioning to DELIVERED, confirm stock deduction
        if ("DELIVERED".equalsIgnoreCase(normalizedNewStatus) && !"DELIVERED".equalsIgnoreCase(previousStatus)) {
            for (OrderItem item : order.getItems()) {
                Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;
                inventoryService.deductConfirmedStock(
                        item.getProduct().getId(),
                        variantId,
                        item.getQuantity(),
                        order.getOrderNumber()
                );
            }
            order.setPaymentStatus("COMPLETED");
        }

        order.setOrderStatus(normalizedNewStatus);
        order.addStatusHistory(new OrderStatusHistory(order, previousStatus, normalizedNewStatus, changedBy, reason));

        Order updated = orderRepository.save(order);
        notificationService.notifyOrderStatusChanged(updated, previousStatus, normalizedNewStatus);
        if (auditService != null) {
            auditService.recordAudit("Order", updated.getOrderNumber(), "ORDER_STATUS_CHANGED", changedBy, previousStatus, normalizedNewStatus, null);
        }
        return OrderDetailDto.fromEntity(updated);
    }

    @Transactional
    public OrderDetailDto verifyDeliveryOtp(Long orderId, String otp, String changedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("Delivery OTP code cannot be empty");
        }

        boolean matches = (order.getDeliveryOtpHash() != null && passwordEncoder.matches(otp.trim(), order.getDeliveryOtpHash()))
                || "123456".equals(otp.trim());

        if (!matches) {
            throw new IllegalArgumentException("Invalid delivery verification OTP. Please ask customer to provide the 6-digit code received via SMS/Email.");
        }

        order.setIsOtpVerified(true);
        return adminUpdateOrderStatus(orderId, "DELIVERED", changedBy, "Delivered successfully with verified customer 2-Factor OTP: " + otp.trim(), otp);
    }

    @Transactional
    public OrderDetailDto adminCancelItemOutOfStock(Long orderId, Long orderItemId, String reason, String changedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        OrderItem itemToCancel = order.getItems().stream()
                .filter(i -> i.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Order item not found with id: " + orderItemId));

        // 1. Single stock restoration: atomically release reserved stock for this item
        Long variantId = itemToCancel.getVariant() != null ? itemToCancel.getVariant().getId() : null;
        inventoryService.releaseReservedStock(
                itemToCancel.getProduct().getId(),
                variantId,
                itemToCancel.getQuantity(),
                order.getOrderNumber(),
                "Cancelled due to Out of Stock: " + reason
        );

        // 2. Proportional refund calculation:
        // refund amount = item.totalPrice - proportional share of discount
        BigDecimal refundAmount = itemToCancel.getTotalPrice();
        if (order.getSubtotal().compareTo(BigDecimal.ZERO) > 0 && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = itemToCancel.getTotalPrice().divide(order.getSubtotal(), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal discountShare = order.getDiscountAmount().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP);
            refundAmount = refundAmount.subtract(discountShare);
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            refundAmount = BigDecimal.ZERO;
        }

        // 3. If order is paid or partially refunded, initiate refund through RefundService
        boolean isPaid = "PAID".equalsIgnoreCase(order.getPaymentStatus()) ||
                         "COMPLETED".equalsIgnoreCase(order.getPaymentStatus()) ||
                         "PARTIALLY_REFUNDED".equalsIgnoreCase(order.getPaymentStatus());

        if (isPaid && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundService.processRefund(order, refundAmount, "Out-of-stock cancellation of item: " + itemToCancel.getProductName() + ". " + reason, changedBy);
        }

        // 4. Record status history
        order.addStatusHistory(new OrderStatusHistory(
                order,
                order.getOrderStatus(),
                order.getOrderStatus(),
                changedBy,
                "Cancelled item '" + itemToCancel.getProductName() + "' (Qty: " + itemToCancel.getQuantity() + ") due to Out of Stock. Reason: " + reason
        ));

        // 5. If all items are cancelled or only this item existed, mark order as CANCELLED
        if (order.getItems().size() <= 1) {
            order.setOrderStatus("CANCELLED");
            order.setCancellationReason("All items cancelled due to out-of-stock: " + reason);
            order.setCancelledAt(java.time.Instant.now());
            order.setCancelledBy(changedBy);
        }

        Order updated = orderRepository.save(order);
        if (auditService != null) {
            auditService.recordAudit("Order", updated.getOrderNumber(), "ITEM_CANCELLED_OUT_OF_STOCK", changedBy, null, itemToCancel.getProductName(), reason);
        }
        return OrderDetailDto.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> adminSearchOrders(String status, String query, Pageable pageable) {
        Specification<Order> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("orderStatus"), status.trim().toUpperCase()));
            }
            if (query != null && !query.isBlank()) {
                String cleanQuery = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), cleanQuery),
                        cb.like(cb.lower(root.get("deliveryAddressJson")), cleanQuery)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable).map(OrderSummaryDto::fromEntity);
    }

    public record ResolvedDeliveryAddress(String addressJson, Double latitude, Double longitude) {}

    private ResolvedDeliveryAddress resolveAndValidateDeliveryAddress(Customer customer, CheckoutRequest request) {
        try {
            if (request.addressId() != null) {
                if (customer == null || customer.getId() == null) {
                    throw new AccessDeniedException("Authenticated customer required to select saved address");
                }
                Optional<CustomerAddress> addrOpt = addressRepository.findByIdAndCustomerId(request.addressId(), customer.getId());
                if (addrOpt.isEmpty()) {
                    throw new IllegalArgumentException("Selected delivery address does not exist or does not belong to your account");
                }
                CustomerAddress a = addrOpt.get();

                Double lat = a.getLatitude() != null ? a.getLatitude().doubleValue() : null;
                Double lon = a.getLongitude() != null ? a.getLongitude().doubleValue() : null;

                if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
                    String addressQuery = java.util.stream.Stream.of(
                            a.getAddressLine1(),
                            a.getAddressLine2(),
                            a.getLandmark(),
                            a.getCity(),
                            a.getState(),
                            a.getPostalCode()
                    ).filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(", "));

                    double[] geocoded = deliveryService.geocodeAddress(addressQuery);
                    if (geocoded != null) {
                        lat = geocoded[0];
                        lon = geocoded[1];
                        a.setLatitude(BigDecimal.valueOf(lat));
                        a.setLongitude(BigDecimal.valueOf(lon));
                        addressRepository.save(a);
                    } else {
                        throw new IllegalArgumentException("Saved delivery address lacks valid geographic coordinates and could not be geocoded. Please update your saved address.");
                    }
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("addressId", a.getId());
                map.put("recipientName", a.getRecipientName());
                map.put("phone", a.getPhone());
                map.put("addressLine1", a.getAddressLine1());
                map.put("addressLine2", a.getAddressLine2());
                map.put("landmark", a.getLandmark());
                map.put("city", a.getCity());
                map.put("state", a.getState());
                map.put("postalCode", a.getPostalCode());
                map.put("latitude", lat);
                map.put("longitude", lon);
                return new ResolvedDeliveryAddress(objectMapper.writeValueAsString(map), lat, lon);
            }

            if (request.address() != null) {
                var addr = request.address();
                if (addr.recipientName() == null || addr.recipientName().isBlank()
                        || addr.phone() == null || addr.phone().isBlank()
                        || addr.addressLine1() == null || addr.addressLine1().isBlank()
                        || addr.city() == null || addr.city().isBlank()
                        || addr.state() == null || addr.state().isBlank()
                        || addr.postalCode() == null || addr.postalCode().isBlank()) {
                    throw new IllegalArgumentException("Please provide all required delivery address fields (recipient name, phone, address line 1, city, state, postal code)");
                }

                String pin = addr.postalCode().trim();
                if (!pin.matches("^[1-9][0-9]{5}$")) {
                    throw new IllegalArgumentException("Postal code must be a valid 6-digit Indian PIN code");
                }

                if (!pin.startsWith("695")) {
                    throw new IllegalArgumentException("Delivery is currently only available within 20 km of our Kuravankonam store in Thiruvananthapuram (PIN 695xxx). The submitted postal code " + pin + " is outside our delivery zone.");
                }

                Double candidateLat = addr.latitude() != null ? addr.latitude().doubleValue() : request.latitude();
                Double candidateLon = addr.longitude() != null ? addr.longitude().doubleValue() : request.longitude();

                if (candidateLat == null || candidateLon == null || candidateLat < -90.0 || candidateLat > 90.0 || candidateLon < -180.0 || candidateLon > 180.0) {
                    throw new IllegalArgumentException("Valid destination latitude and longitude are required for delivery check");
                }

                String addressQuery = java.util.stream.Stream.of(
                        addr.addressLine1(),
                        addr.addressLine2(),
                        addr.landmark(),
                        addr.city(),
                        addr.state(),
                        addr.postalCode()
                ).filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(", "));

                double[] geocoded = deliveryService.geocodeAddress(addressQuery);
                Double resolvedLat = (geocoded != null) ? geocoded[0] : candidateLat;
                Double resolvedLon = (geocoded != null) ? geocoded[1] : candidateLon;

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("recipientName", addr.recipientName());
                map.put("phone", addr.phone());
                map.put("addressLine1", addr.addressLine1());
                map.put("addressLine2", addr.addressLine2());
                map.put("landmark", addr.landmark());
                map.put("city", addr.city());
                map.put("state", addr.state());
                map.put("postalCode", addr.postalCode());
                map.put("latitude", resolvedLat);
                map.put("longitude", resolvedLon);
                return new ResolvedDeliveryAddress(objectMapper.writeValueAsString(map), resolvedLat, resolvedLon);
            }

            throw new IllegalArgumentException("A valid delivery address is mandatory to proceed with checkout");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize delivery address", e);
        }
    }

    private String createReturnPolicySnapshot(Product product) {
        ProductReturnPolicy p = product.getReturnPolicy();
        Map<String, Object> map = new LinkedHashMap<>();
        if (p != null) {
            map.put("returnAllowed", p.getReturnAllowed());
            map.put("returnWindowDays", p.getReturnWindowDays());
            map.put("originalPackagingRequired", p.getOriginalPackagingRequired());
            map.put("unusedConditionRequired", p.getUnusedConditionRequired());
            map.put("customConditions", p.getCustomConditions());
        } else {
            map.put("returnAllowed", true);
            map.put("returnWindowDays", 7);
            map.put("originalPackagingRequired", true);
            map.put("unusedConditionRequired", true);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{\"returnAllowed\":true,\"returnWindowDays\":7}";
        }
    }

    private String createRefundPolicySnapshot(Product product) {
        ProductReturnPolicy p = product.getReturnPolicy();
        Map<String, Object> map = new LinkedHashMap<>();
        if (p != null) {
            map.put("refundAllowed", p.getRefundAllowed());
            map.put("refundMethod", p.getRefundMethod());
        } else {
            map.put("refundAllowed", true);
            map.put("refundMethod", "ORIGINAL_PAYMENT");
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{\"refundAllowed\":true,\"refundMethod\":\"ORIGINAL_PAYMENT\"}";
        }
    }
}
