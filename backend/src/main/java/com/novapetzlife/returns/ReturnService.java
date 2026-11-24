package com.novapetzlife.returns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.order.*;
import com.novapetzlife.refund.RefundService;
import com.novapetzlife.returns.dto.CreateReturnRequest;
import com.novapetzlife.returns.dto.InspectReturnRequest;
import com.novapetzlife.returns.dto.ReturnEligibilityDto;
import com.novapetzlife.returns.dto.ReturnRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnInspectionRepository returnInspectionRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final RefundService refundService;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final com.novapetzlife.order.OrderStatusHistoryRepository statusHistoryRepository;

    public ReturnService(
            ReturnRequestRepository returnRequestRepository,
            ReturnRequestItemRepository returnRequestItemRepository,
            ReturnInspectionRepository returnInspectionRepository,
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService,
            RefundService refundService,
            com.novapetzlife.notification.NotificationService notificationService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.order.OrderStatusHistoryRepository statusHistoryRepository) {
        this.returnRequestRepository = returnRequestRepository;
        this.returnRequestItemRepository = returnRequestItemRepository;
        this.returnInspectionRepository = returnInspectionRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.refundService = refundService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    private Optional<LocalDate> resolveDeliveryDate(Order order) {
        if (statusHistoryRepository != null && order.getId() != null) {
            List<com.novapetzlife.order.OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
            for (com.novapetzlife.order.OrderStatusHistory h : history) {
                if ("DELIVERED".equalsIgnoreCase(h.getNewStatus()) && h.getCreatedAt() != null) {
                    return Optional.of(LocalDate.ofInstant(h.getCreatedAt(), ZoneId.of("Asia/Kolkata")));
                }
            }
        }
        // Fail safely: Never silently fallback to order.createdAt, which penalizes customers for long shipping times
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public ReturnEligibilityDto checkOrderReturnEligibility(String orderNumber, Customer customer) {
        return checkOrderReturnEligibility(orderNumber, customer, customer == null);
    }

    @Transactional(readOnly = true)
    public ReturnEligibilityDto checkOrderReturnEligibility(String orderNumber, Customer customer, boolean isAdmin) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        if (!isAdmin) {
            if (customer == null || order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to view return eligibility for this order.");
            }
        }

        String orderStatus = order.getOrderStatus();
        Optional<LocalDate> deliveryDateOpt = resolveDeliveryDate(order);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // Check if order was delivered
        boolean isDelivered = "DELIVERED".equalsIgnoreCase(orderStatus);
        boolean deliveryDateVerified = deliveryDateOpt.isPresent();

        String generalMessage;
        if (!isDelivered) {
            generalMessage = "Return requests can only be submitted for DELIVERED orders. Current order status: " + orderStatus;
        } else if (!deliveryDateVerified) {
            generalMessage = "Order is marked DELIVERED but lacks a verified delivery timestamp in store records. Please contact store support for manual return authorization.";
        } else {
            generalMessage = "Order is eligible for return evaluation under NOVA PETZLIFE store policy.";
        }

        List<ReturnEligibilityDto.ItemEligibilityDto> itemDtos = new ArrayList<>();
        boolean anyEligible = false;

        for (OrderItem item : order.getItems()) {
            boolean returnAllowed = false;
            int returnWindowDays = 0;
            boolean packagingRequired = true;
            boolean unusedRequired = true;
            String conditions = "";

            try {
                JsonNode snapshot = objectMapper.readTree(item.getReturnPolicySnapshot());
                returnAllowed = snapshot.path("returnAllowed").asBoolean(false);
                returnWindowDays = snapshot.path("returnWindowDays").asInt(0);
                packagingRequired = snapshot.path("originalPackagingRequired").asBoolean(true);
                unusedRequired = snapshot.path("unusedConditionRequired").asBoolean(true);
                conditions = snapshot.path("conditions").asText("");
            } catch (Exception e) {
                log.warn("Could not parse return policy snapshot for item {}: {}", item.getId(), e.getMessage());
            }

            final int windowDays = returnWindowDays;
            LocalDate deadline = deliveryDateOpt.map(d -> d.plusDays(windowDays)).orElse(null);
            boolean withinWindow = deadline != null && !today.isAfter(deadline);
            boolean isEligible = isDelivered && deliveryDateVerified && returnAllowed && withinWindow;

            String reasonIfNotEligible = null;
            if (!isDelivered) {
                reasonIfNotEligible = "Order is not yet marked as delivered.";
            } else if (!deliveryDateVerified) {
                reasonIfNotEligible = "Delivery date could not be verified from store tracking records. Admin manual authorization required.";
            } else if (!returnAllowed) {
                reasonIfNotEligible = "This consumable/perishable item is non-returnable under store policy.";
            } else if (!withinWindow) {
                reasonIfNotEligible = "Return window of " + returnWindowDays + " days expired on " + deadline + ".";
            }

            if (isEligible) {
                anyEligible = true;
            }

            itemDtos.add(new ReturnEligibilityDto.ItemEligibilityDto(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getBrandName(),
                    item.getSku(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    returnAllowed,
                    returnWindowDays,
                    deadline,
                    withinWindow,
                    isEligible,
                    reasonIfNotEligible,
                    packagingRequired,
                    unusedRequired,
                    conditions
            ));
        }

        return new ReturnEligibilityDto(
                order.getOrderNumber(),
                order.getOrderStatus(),
                deliveryDateOpt.map(LocalDate::toString).orElse(null),
                anyEligible,
                generalMessage,
                itemDtos
        );
    }

    @Transactional
    public ReturnRequestDto createReturnRequest(Customer customer, CreateReturnRequest request) {
        Order order = orderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + request.getOrderNumber()));

        if (customer == null || order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create a return request for this order.");
        }

        if (!"DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new IllegalStateException("Return requests can only be placed for delivered orders. Current status: " + order.getOrderStatus());
        }

        Optional<LocalDate> deliveryDateOpt = resolveDeliveryDate(order);
        if (deliveryDateOpt.isEmpty()) {
            throw new IllegalStateException("Order delivery date could not be verified from store tracking history. Please contact customer support for manual return authorization.");
        }
        LocalDate effectiveDeliveryDate = deliveryDateOpt.get();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // Validate items and quantities
        Map<Long, OrderItem> orderItemMap = new HashMap<>();
        for (OrderItem it : order.getItems()) {
            orderItemMap.put(it.getId(), it);
        }

        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String requestNumber = "RET-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;

        ReturnRequest returnRequest = new ReturnRequest(
                requestNumber,
                order,
                customer,
                request.getReason(),
                request.getCustomerComments()
        );

        for (CreateReturnRequest.ReturnItemRequest itemReq : request.getItems()) {
            OrderItem orderItem = orderItemMap.get(itemReq.getOrderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException("Order item " + itemReq.getOrderItemId() + " does not belong to order " + order.getOrderNumber());
            }

            if (itemReq.getQuantity() <= 0 || itemReq.getQuantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException("Invalid return quantity " + itemReq.getQuantity() + " for item " + orderItem.getProductName());
            }

            // Verify item-level snapshot eligibility
            try {
                JsonNode snapshot = objectMapper.readTree(orderItem.getReturnPolicySnapshot());
                boolean allowed = snapshot.path("returnAllowed").asBoolean(false);
                int days = snapshot.path("returnWindowDays").asInt(0);
                if (!allowed) {
                    throw new IllegalArgumentException("Item " + orderItem.getProductName() + " is marked as non-returnable");
                }
                LocalDate deadline = effectiveDeliveryDate.plusDays(days);
                if (today.isAfter(deadline)) {
                    throw new IllegalArgumentException("Return window expired for item " + orderItem.getProductName() + " on " + deadline);
                }
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
                throw new IllegalStateException("Error validating item policy snapshot", e);
            }

            ReturnRequestItem reqItem = new ReturnRequestItem(returnRequest, orderItem, itemReq.getQuantity());
            returnRequest.addItem(reqItem);
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        log.info("Created return request {} for order {}", saved.getRequestNumber(), order.getOrderNumber());
        notificationService.notifyReturnRequested(saved);
        return ReturnRequestDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReturnRequestDto> getReturnRequests(String status, Pageable pageable) {
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            return returnRequestRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase().trim(), pageable)
                    .map(ReturnRequestDto::fromEntity);
        }
        return returnRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ReturnRequestDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ReturnRequestDto getReturnRequestByNumber(String requestNumber) {
        return getReturnRequestByNumber(requestNumber, null, true);
    }

    @Transactional(readOnly = true)
    public ReturnRequestDto getReturnRequestByNumber(String requestNumber, Customer customer, boolean isAdmin) {
        ReturnRequest req = returnRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found with number: " + requestNumber));

        if (!isAdmin) {
            if (customer == null || req.getCustomer() == null || !customer.getId().equals(req.getCustomer().getId())) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to view this return request.");
            }
        }
        return ReturnRequestDto.fromEntity(req);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestDto> getReturnRequestsByOrderId(Long orderId) {
        return returnRequestRepository.findByOrderId(orderId).stream()
                .map(ReturnRequestDto::fromEntity)
                .toList();
    }

    @Transactional
    public ReturnRequestDto adminSchedulePickup(Long returnRequestId, String adminNotes, String staffUser) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found with id: " + returnRequestId));

        req.setStatus("PICKUP_SCHEDULED");
        if (adminNotes != null) req.setAdminNotes(adminNotes);
        return ReturnRequestDto.fromEntity(returnRequestRepository.save(req));
    }

    @Transactional
    public ReturnRequestDto adminReceiveReturn(Long returnRequestId, String adminNotes, String staffUser) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found with id: " + returnRequestId));

        req.setStatus("RECEIVED");
        if (adminNotes != null) req.setAdminNotes(adminNotes);
        return ReturnRequestDto.fromEntity(returnRequestRepository.save(req));
    }

    @Transactional
    public ReturnRequestDto adminInspectReturn(Long returnRequestId, InspectReturnRequest request, String staffUser) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found with id: " + returnRequestId));

        String outcome = request.getOutcome().toUpperCase().trim();
        ReturnInspection inspection = new ReturnInspection(req, staffUser, outcome, request.getConditionNotes());
        req.addInspection(inspection);

        if ("APPROVED".equals(outcome) || "PARTIALLY_APPROVED".equals(outcome)) {
            // 1. Restock returned items if requested and condition is acceptable
            if (request.isRestockInventory()) {
                for (ReturnRequestItem it : req.getItems()) {
                    OrderItem orderItem = it.getOrderItem();
                    Long variantId = orderItem.getVariant() != null ? orderItem.getVariant().getId() : null;
                    inventoryService.restockReturnedItem(
                            orderItem.getProduct().getId(),
                            variantId,
                            it.getQuantity(),
                            req.getRequestNumber(),
                            "Returned stock restored to store inventory after physical inspection"
                    );
                }
            }

            // 2. Process refund if approved
            if (request.isApproveRefund()) {
                BigDecimal refundAmount = request.getRefundAmount();
                if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    refundAmount = BigDecimal.ZERO;
                    for (ReturnRequestItem it : req.getItems()) {
                        refundAmount = refundAmount.add(it.getOrderItem().getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
                    }
                }
                refundService.processRefund(
                        req.getOrder(),
                        refundAmount,
                        "Approved return refund for request " + req.getRequestNumber(),
                        staffUser
                );
                req.setStatus("CLOSED");
            } else {
                req.setStatus("INSPECTED");
            }
        } else {
            // REJECTED
            req.setStatus("REJECTED");
        }

        if (request.getAdminNotes() != null) {
            req.setAdminNotes(request.getAdminNotes());
        }

        return ReturnRequestDto.fromEntity(returnRequestRepository.save(req));
    }

    @Transactional
    public OrderDetailDto cancelOrder(String orderNumber, String reason, String operator, Customer customer, boolean isAdmin) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        String currentStatus = order.getOrderStatus();

        // Idempotency: if already cancelled, return existing state safely
        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            return OrderDetailDto.fromEntity(order);
        }

        // 1. Ownership validation
        if (!isAdmin) {
            if (customer == null || order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to cancel this order.");
            }
        }

        // 2. State validation: Customer cancellation allowed ONLY before packing
        if (!isAdmin) {
            boolean allowed = "PENDING_PAYMENT".equalsIgnoreCase(currentStatus)
                    || "PENDING".equalsIgnoreCase(currentStatus)
                    || "CONFIRMED".equalsIgnoreCase(currentStatus)
                    || "PAID".equalsIgnoreCase(currentStatus);
            if (!allowed) {
                throw new IllegalStateException("Order cannot be cancelled because it has already reached status: "
                        + currentStatus + ". Customers can only cancel orders before packing has begun.");
            }
        } else {
            if ("DELIVERED".equalsIgnoreCase(currentStatus)
                    || "OUT_FOR_DELIVERY".equalsIgnoreCase(currentStatus)
                    || "RETURNED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Order cannot be cancelled in status: " + currentStatus + ". Please use the return request workflow if delivered.");
            }
        }

        // Release reserved stock for each item
        for (OrderItem item : order.getItems()) {
            Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;
            inventoryService.releaseReservedStock(
                    item.getProduct().getId(),
                    variantId,
                    item.getQuantity(),
                    order.getOrderNumber(),
                    "Order cancelled by " + operator + ": " + reason
            );
        }

        // If online payment was already completed, automatically trigger full refund
        if ("COMPLETED".equalsIgnoreCase(order.getPaymentStatus()) || "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            refundService.processRefund(
                    order,
                    order.getFinalPayableAmount(),
                    "Order cancellation refund (" + reason + ")",
                    operator
            );
        }

        order.setOrderStatus("CANCELLED");
        order.setCancellationReason(reason);
        order.addStatusHistory(new OrderStatusHistory(
                order,
                currentStatus,
                "CANCELLED",
                operator,
                reason
        ));
        order = orderRepository.save(order);
        notificationService.notifyOrderStatusChanged(order, currentStatus, "CANCELLED");

        log.info("Order {} successfully cancelled by {}. Reason: {}", order.getOrderNumber(), operator, reason);
        return OrderDetailDto.fromEntity(order);
    }

    @Transactional
    public OrderDetailDto cancelOrder(String orderNumber, String reason, String cancelledBy, boolean isCustomer) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));
        Customer customer = order.getCustomer();
        return cancelOrder(orderNumber, reason, cancelledBy, customer, !isCustomer);
    }
}
