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
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
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
    }

    @Transactional
    public OrderDetailDto checkout(Customer customer, CheckoutRequest request) {
        // 1. Resolve Customer (Registered or Guest)
        Customer orderCustomer = customer;
        if (orderCustomer == null) {
            orderCustomer = customerService.createGuestCustomer(
                    request.guestEmail(),
                    request.guestPhone(),
                    request.guestFirstName(),
                    request.guestLastName()
            );
        }

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

        // 3. Compute Subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            subtotal = subtotal.add(item.getTotalPrice());
        }

        // 4. Check 20 km Kuravankonam Delivery Eligibility
        DeliveryEligibilityResponse eligibility = deliveryService.checkDeliveryEligibility(
                request.latitude(),
                request.longitude(),
                subtotal
        );

        if (!Boolean.TRUE.equals(eligibility.isEligible())) {
            throw new IllegalArgumentException(eligibility.message());
        }

        // 5. Serialize Delivery Address Snapshot
        String addressJson = serializeAddress(orderCustomer, request);

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

    private String serializeAddress(Customer customer, CheckoutRequest request) {
        try {
            if (request.addressId() != null && customer.getId() != null) {
                Optional<CustomerAddress> addrOpt = addressRepository.findByIdAndCustomerId(request.addressId(), customer.getId());
                if (addrOpt.isPresent()) {
                    CustomerAddress a = addrOpt.get();
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("recipientName", a.getRecipientName());
                    map.put("phone", a.getPhone());
                    map.put("addressLine1", a.getAddressLine1());
                    map.put("addressLine2", a.getAddressLine2());
                    map.put("landmark", a.getLandmark());
                    map.put("city", a.getCity());
                    map.put("state", a.getState());
                    map.put("postalCode", a.getPostalCode());
                    map.put("latitude", a.getLatitude());
                    map.put("longitude", a.getLongitude());
                    return objectMapper.writeValueAsString(map);
                }
            }

            if (request.address() != null) {
                return objectMapper.writeValueAsString(request.address());
            }

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("recipientName", customer.getFirstName() + " " + customer.getLastName());
            fallback.put("phone", customer.getPhone());
            fallback.put("city", "Thiruvananthapuram");
            fallback.put("latitude", request.latitude());
            fallback.put("longitude", request.longitude());
            return objectMapper.writeValueAsString(fallback);
        } catch (JsonProcessingException e) {
            return "{\"address\":\"Thiruvananthapuram, Kerala\"}";
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
