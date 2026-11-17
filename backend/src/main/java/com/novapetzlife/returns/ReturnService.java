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

    public ReturnService(
            ReturnRequestRepository returnRequestRepository,
            ReturnRequestItemRepository returnRequestItemRepository,
            ReturnInspectionRepository returnInspectionRepository,
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService,
            RefundService refundService,
            com.novapetzlife.notification.NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.returnRequestRepository = returnRequestRepository;
        this.returnRequestItemRepository = returnRequestItemRepository;
        this.returnInspectionRepository = returnInspectionRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.refundService = refundService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReturnEligibilityDto checkOrderReturnEligibility(String orderNumber, Customer customer) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        String orderStatus = order.getOrderStatus();
        LocalDate orderLocalDate = LocalDate.ofInstant(order.getCreatedAt(), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();

        // Check if order was delivered
        boolean isDelivered = "DELIVERED".equalsIgnoreCase(orderStatus);
        String generalMessage = isDelivered
                ? "Order is eligible for return evaluation under NOVA PETZLIFE store policy."
                : "Return requests can only be submitted for DELIVERED orders. Current order status: " + orderStatus;

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

            LocalDate deadline = orderLocalDate.plusDays(returnWindowDays);
            boolean withinWindow = !today.isAfter(deadline);
            boolean isEligible = isDelivered && returnAllowed && withinWindow;

            String reasonIfNotEligible = null;
            if (!isDelivered) {
                reasonIfNotEligible = "Order is not yet marked as delivered.";
            } else if (!returnAllowed) {
                reasonIfNotEligible = "This consumable/perishable item is non-returnable under store policy.";
            } else if (!withinWindow) {
                reasonIfNotEligible = "Return window of " + returnWindowDays + " days expired on " + deadline + ".";
            }

            if (isEligible) {
                anyEligible = true;
            }

            Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;

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
                orderLocalDate.toString(),
                anyEligible,
                generalMessage,
                itemDtos
        );
    }

    @Transactional
    public ReturnRequestDto createReturnRequest(Customer customer, CreateReturnRequest request) {
        Order order = orderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + request.getOrderNumber()));

        if (!"DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new IllegalStateException("Return requests can only be placed for delivered orders. Current status: " + order.getOrderStatus());
        }

        Customer requestCustomer = customer != null ? customer : order.getCustomer();
        LocalDate today = LocalDate.now();
        LocalDate orderLocalDate = LocalDate.ofInstant(order.getCreatedAt(), ZoneId.systemDefault());

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
                requestCustomer,
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
                LocalDate deadline = orderLocalDate.plusDays(days);
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
        ReturnRequest req = returnRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found with number: " + requestNumber));
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
    public OrderDetailDto cancelOrder(String orderNumber, String reason, String cancelledBy, boolean isCustomer) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        String currentStatus = order.getOrderStatus();
        if ("DELIVERED".equalsIgnoreCase(currentStatus)
                || "OUT_FOR_DELIVERY".equalsIgnoreCase(currentStatus)
                || "CANCELLED".equalsIgnoreCase(currentStatus)
                || "RETURNED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Order cannot be cancelled in status: " + currentStatus + ". Please use the return request workflow if delivered.");
        }

        // Release reserved stock for each item
        for (OrderItem item : order.getItems()) {
            Long variantId = item.getVariant() != null ? item.getVariant().getId() : null;
            inventoryService.releaseReservedStock(
                    item.getProduct().getId(),
                    variantId,
                    item.getQuantity(),
                    order.getOrderNumber(),
                    "Order cancelled by " + cancelledBy + ": " + reason
            );
        }

        // If online payment was already completed, automatically trigger full refund
        if ("COMPLETED".equalsIgnoreCase(order.getPaymentStatus())) {
            refundService.processRefund(
                    order,
                    order.getFinalPayableAmount(),
                    "Order cancellation refund (" + reason + ")",
                    cancelledBy
            );
        }

        order.setOrderStatus("CANCELLED");
        order.setCancellationReason(reason);
        order.addStatusHistory(new OrderStatusHistory(
                order,
                currentStatus,
                "CANCELLED",
                cancelledBy,
                reason
        ));
        order = orderRepository.save(order);
        notificationService.notifyOrderStatusChanged(order, currentStatus, "CANCELLED");

        log.info("Order {} successfully cancelled by {}. Reason: {}", order.getOrderNumber(), cancelledBy, reason);
        return OrderDetailDto.fromEntity(order);
    }
}
