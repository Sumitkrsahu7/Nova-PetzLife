package com.novapetzlife.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.delivery.DeliveryService;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TaxConfigurationRepository taxConfigRepository;
    private final OrderRepository orderRepository;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    // Confirmed Store Details for GST Invoicing
    private static final String STORE_NAME = "NOVA PETZLIFE";
    private static final String STORE_STATE = "Kerala";
    private static final String STATE_CODE = "32";
    private static final String STORE_GSTIN = "32AAAAA0000A1Z5";
    private static final String STORE_PHONE = "+91 94471 23456";

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            TaxConfigurationRepository taxConfigRepository,
            OrderRepository orderRepository,
            DeliveryService deliveryService,
            ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.taxConfigRepository = taxConfigRepository;
        this.orderRepository = orderRepository;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Invoice generateInvoiceForOrder(Order order) {
        Optional<Invoice> existing = invoiceRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Generate invoice number: INV-YYYYMMDD-XXXX
        String suffix = order.getOrderNumber().length() >= 6
                ? order.getOrderNumber().substring(order.getOrderNumber().length() - 6)
                : String.valueOf(order.getId());
        String invoiceNumber = "INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;

        BigDecimal totalGross = order.getTotalAmount();
        // Indian GST standard inclusive calculation (18% GST = 9% CGST + 9% SGST)
        BigDecimal gstFactor = new BigDecimal("1.18");
        BigDecimal taxableValue = totalGross.divide(gstFactor, 2, RoundingMode.HALF_UP);
        BigDecimal totalGst = totalGross.subtract(taxableValue);
        BigDecimal cgst = totalGst.divide(new BigDecimal("2.00"), 2, RoundingMode.HALF_UP);
        BigDecimal sgst = totalGst.subtract(cgst);

        String customerName = order.getCustomer() != null
                ? (order.getCustomer().getFirstName() + " " + (order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "")).trim()
                : "Customer";

        Invoice invoice = new Invoice(
                order,
                invoiceNumber,
                LocalDate.now(),
                customerName,
                null,
                taxableValue,
                cgst,
                sgst,
                BigDecimal.ZERO,
                order.getFinalPayableAmount()
        );

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public InvoiceDto getInvoiceDtoByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        Invoice invoice = generateInvoiceForOrder(order);
        ShopLocation shop = deliveryService.getShopLocation();

        List<InvoiceDto.InvoiceItemDto> itemDtos = new ArrayList<>();
        int itemIndex = 1;

        for (OrderItem item : order.getItems()) {
            BigDecimal lineGross = item.getTotalPrice();
            BigDecimal taxable = lineGross.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            BigDecimal gst = lineGross.subtract(taxable);
            BigDecimal cgst = gst.divide(new BigDecimal("2.00"), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = gst.subtract(cgst);

            String hsn = (item.getHsnCode() != null && !item.getHsnCode().isBlank()) ? item.getHsnCode() : "2309";
            String returnPolicyText = parseReturnPolicy(item.getReturnPolicySnapshot());

            itemDtos.add(new InvoiceDto.InvoiceItemDto(
                    itemIndex++,
                    item.getProductName(),
                    item.getBrandName(),
                    item.getSku(),
                    hsn,
                    item.getQuantity(),
                    item.getUnitPrice(),
                    taxable,
                    new BigDecimal("9.00"),
                    cgst,
                    new BigDecimal("9.00"),
                    sgst,
                    lineGross,
                    returnPolicyText
            ));
        }

        String formattedAddress = parseAddress(order.getDeliveryAddressJson());
        String amountInWords = convertAmountToWords(order.getFinalPayableAmount());

        return new InvoiceDto(
                STORE_NAME,
                shop.getAddressLine(),
                shop.getCity(),
                STORE_STATE,
                STATE_CODE,
                shop.getPinCode(),
                STORE_GSTIN,
                STORE_PHONE,
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                order.getOrderNumber(),
                order.getCreatedAt().toString(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getDeliverySlot(),
                "Kerala (32)",
                "No",
                invoice.getCustomerName(),
                order.getCustomer() != null ? order.getCustomer().getPhone() : "",
                order.getCustomer() != null ? order.getCustomer().getEmail() : "",
                formattedAddress,
                itemDtos,
                invoice.getTotalTaxableValue(),
                invoice.getTotalCgst(),
                invoice.getTotalSgst(),
                BigDecimal.ZERO,
                order.getDeliveryCharge(),
                order.getFinalPayableAmount(),
                amountInWords
        );
    }

    private String parseAddress(String json) {
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            List<String> parts = new ArrayList<>();
            if (map.get("recipientName") != null) parts.add(map.get("recipientName").toString());
            if (map.get("addressLine1") != null) parts.add(map.get("addressLine1").toString());
            if (map.get("addressLine2") != null) parts.add(map.get("addressLine2").toString());
            if (map.get("landmark") != null) parts.add("Near " + map.get("landmark").toString());
            if (map.get("city") != null) parts.add(map.get("city").toString());
            if (map.get("postalCode") != null) parts.add("PIN: " + map.get("postalCode").toString());
            return String.join(", ", parts);
        } catch (Exception e) {
            return "Thiruvananthapuram, Kerala";
        }
    }

    private String parseReturnPolicy(String snapshotJson) {
        try {
            Map<?, ?> map = objectMapper.readValue(snapshotJson, Map.class);
            boolean allowed = Boolean.TRUE.equals(map.get("returnAllowed"));
            if (!allowed) {
                return "Non-returnable consumable";
            }
            Object days = map.get("returnWindowDays");
            return days + "-day return window (unopened & original packaging)";
        } catch (Exception e) {
            return "Standard store return policy applies";
        }
    }

    private String convertAmountToWords(BigDecimal amount) {
        long rupees = amount.longValue();
        return "Rupees " + rupees + " Only";
    }
}
