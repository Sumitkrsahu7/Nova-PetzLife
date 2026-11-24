package com.novapetzlife.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.common.ResourceNotFoundException;
import com.novapetzlife.delivery.DeliveryService;
import com.novapetzlife.delivery.ShopLocation;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String STORE_PHONE = "+91 94471 23456";

    @Value("${app.store.gstin:}")
    private String configuredGstin;

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

    public boolean hasConfiguredGstin() {
        if (configuredGstin == null || configuredGstin.isBlank()) {
            return false;
        }
        return configuredGstin.trim().toUpperCase().matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");
    }

    public boolean isGstInvoiceEligible(Order order) {
        // 1. Valid store GSTIN
        if (!hasConfiguredGstin()) {
            return false;
        }

        // 2. Verified shop location
        ShopLocation shop = deliveryService.getShopLocation();
        if (shop == null || shop.getAddressLine() == null || shop.getCity() == null || shop.getPinCode() == null) {
            return false;
        }

        // 3. Store active tax configuration
        List<TaxConfiguration> activeConfigs = taxConfigRepository.findByIsActiveTrue();
        if (activeConfigs == null || activeConfigs.isEmpty()) {
            return false;
        }

        // 4. Product-level tax and HSN validation for every item
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return false;
        }

        for (OrderItem item : order.getItems()) {
            if (item.getHsnCode() == null || item.getHsnCode().isBlank()) {
                return false;
            }
            Optional<TaxConfiguration> taxOpt = taxConfigRepository.findByHsnCode(item.getHsnCode().trim());
            if (taxOpt.isEmpty() || !Boolean.TRUE.equals(taxOpt.get().getIsActive())) {
                return false;
            }
            TaxConfiguration config = taxOpt.get();
            if (config.getCgstRate() == null || config.getSgstRate() == null) {
                return false;
            }
        }

        return true;
    }

    private record CalculatedTax(BigDecimal taxable, BigDecimal cgstRate, BigDecimal cgst, BigDecimal sgstRate, BigDecimal sgst) {}

    private BigDecimal getEffectiveItemGross(OrderItem item, Order order) {
        BigDecimal lineGross = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
        if (order != null && order.getTotalAmount() != null && order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                && order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = lineGross.divide(order.getTotalAmount(), 6, RoundingMode.HALF_UP);
            BigDecimal discountShare = order.getDiscountAmount().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            lineGross = lineGross.subtract(discountShare);
            if (lineGross.compareTo(BigDecimal.ZERO) < 0) {
                lineGross = BigDecimal.ZERO;
            }
        }
        return lineGross;
    }

    private CalculatedTax calculateItemTax(OrderItem item, boolean isGstEligible) {
        return calculateItemTax(item, null, isGstEligible);
    }

    private CalculatedTax calculateItemTax(OrderItem item, BigDecimal lineGross, boolean isGstEligible) {
        if (lineGross == null) {
            lineGross = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
        }
        if (!isGstEligible || item.getHsnCode() == null || item.getHsnCode().isBlank()) {
            return new CalculatedTax(lineGross, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Optional<TaxConfiguration> taxOpt = taxConfigRepository.findByHsnCode(item.getHsnCode().trim());
        if (taxOpt.isEmpty() || !Boolean.TRUE.equals(taxOpt.get().getIsActive())) {
            return new CalculatedTax(lineGross, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TaxConfiguration config = taxOpt.get();
        BigDecimal cgstRate = config.getCgstRate() != null ? config.getCgstRate() : BigDecimal.ZERO;
        BigDecimal sgstRate = config.getSgstRate() != null ? config.getSgstRate() : BigDecimal.ZERO;
        BigDecimal totalGstRate = cgstRate.add(sgstRate);

        if (totalGstRate.compareTo(BigDecimal.ZERO) <= 0) {
            return new CalculatedTax(lineGross, cgstRate, BigDecimal.ZERO, sgstRate, BigDecimal.ZERO);
        }

        BigDecimal rateFactor = BigDecimal.ONE.add(totalGstRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal taxable = lineGross.divide(rateFactor, 2, RoundingMode.HALF_UP);
        BigDecimal totalTax = lineGross.subtract(taxable);

        BigDecimal cgst;
        BigDecimal sgst;
        if (cgstRate.compareTo(BigDecimal.ZERO) > 0 && sgstRate.compareTo(BigDecimal.ZERO) > 0) {
            cgst = totalTax.multiply(cgstRate).divide(totalGstRate, 2, RoundingMode.HALF_UP);
            sgst = totalTax.subtract(cgst);
        } else if (cgstRate.compareTo(BigDecimal.ZERO) > 0) {
            cgst = totalTax;
            sgst = BigDecimal.ZERO;
        } else {
            cgst = BigDecimal.ZERO;
            sgst = totalTax;
        }

        return new CalculatedTax(taxable, cgstRate, cgst, sgstRate, sgst);
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

        boolean isGstEligible = isGstInvoiceEligible(order);
        BigDecimal taxableValue = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;

        if (isGstEligible && order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                BigDecimal effectiveGross = getEffectiveItemGross(item, order);
                CalculatedTax tax = calculateItemTax(item, effectiveGross, true);
                taxableValue = taxableValue.add(tax.taxable());
                cgst = cgst.add(tax.cgst());
                sgst = sgst.add(tax.sgst());
            }
        } else {
            taxableValue = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            cgst = BigDecimal.ZERO;
            sgst = BigDecimal.ZERO;
        }

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
        invoice.setDocumentType(isGstEligible ? "GST_TAX_INVOICE" : "PROVISIONAL_RECEIPT");
        invoice.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public InvoiceDto getInvoiceDtoByOrder(Order order) {
        Invoice invoice = generateInvoiceForOrder(order);
        ShopLocation shop = deliveryService.getShopLocation();

        // TASK 2: Historical Invoice Stability
        // Strictly honor the document classification persisted on the invoice.
        // If an invoice was created as a PROVISIONAL_RECEIPT, it remains a PROVISIONAL_RECEIPT forever,
        // even if GSTIN is configured at a later date.
        // A document is ONLY rendered as a GST_TAX_INVOICE if the persisted documentType is 'GST_TAX_INVOICE'
        // AND the order remains GST-eligible.
        boolean isGstInvoice = "GST_TAX_INVOICE".equalsIgnoreCase(invoice.getDocumentType()) && isGstInvoiceEligible(order);

        List<InvoiceDto.InvoiceItemDto> itemDtos = new ArrayList<>();
        int itemIndex = 1;

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                BigDecimal lineGross = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                BigDecimal effectiveGross = isGstInvoice ? getEffectiveItemGross(item, order) : lineGross;
                CalculatedTax tax = calculateItemTax(item, effectiveGross, isGstInvoice);

                // Never fabricate or fallback to "2309" if unverified
                String hsn = (item.getHsnCode() != null && !item.getHsnCode().isBlank()) ? item.getHsnCode().trim() : null;
                String returnPolicyText = parseReturnPolicy(item.getReturnPolicySnapshot());

                itemDtos.add(new InvoiceDto.InvoiceItemDto(
                        itemIndex++,
                        item.getProductName(),
                        item.getBrandName(),
                        item.getSku(),
                        hsn,
                        item.getQuantity(),
                        item.getUnitPrice(),
                        tax.taxable(),
                        tax.cgstRate(),
                        tax.cgst(),
                        tax.sgstRate(),
                        tax.sgst(),
                        isGstInvoice ? effectiveGross : lineGross,
                        returnPolicyText
                ));
            }
        }

        String formattedAddress = parseAddress(order.getDeliveryAddressJson());
        String amountInWords = convertAmountToWords(order.getFinalPayableAmount());

        String gstin = isGstInvoice ? configuredGstin.trim() : "";
        BigDecimal totalTaxable = isGstInvoice ? invoice.getTotalTaxableValue() : (order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        BigDecimal totalCgst = isGstInvoice ? invoice.getTotalCgst() : BigDecimal.ZERO;
        BigDecimal totalSgst = isGstInvoice ? invoice.getTotalSgst() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;

        return new InvoiceDto(
                STORE_NAME,
                shop.getAddressLine(),
                shop.getCity(),
                STORE_STATE,
                STATE_CODE,
                shop.getPinCode(),
                gstin,
                STORE_PHONE,
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                order.getOrderNumber(),
                order.getCreatedAt().toString(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getDeliverySlot(),
                isGstInvoice ? "Kerala (32)" : "Kerala",
                isGstInvoice ? "No" : "N/A",
                invoice.getCustomerName(),
                order.getCustomer() != null ? order.getCustomer().getPhone() : "",
                order.getCustomer() != null ? order.getCustomer().getEmail() : "",
                formattedAddress,
                itemDtos,
                totalTaxable,
                totalCgst,
                totalSgst,
                BigDecimal.ZERO,
                discount,
                order.getDeliveryCharge() != null ? order.getDeliveryCharge() : BigDecimal.ZERO,
                order.getFinalPayableAmount(),
                amountInWords,
                isGstInvoice ? "GST_TAX_INVOICE" : "PROVISIONAL_RECEIPT"
        );
    }

    @Transactional
    public InvoiceDto getInvoiceDtoByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order: " + orderNumber));
        return getInvoiceDtoByOrder(order);
    }

    private String parseAddress(String json) {
        if (!org.springframework.util.StringUtils.hasText(json)) {
            return "";
        }
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            List<String> parts = new ArrayList<>();
            if (map.get("recipientName") != null) parts.add(map.get("recipientName").toString());
            if (map.get("addressLine1") != null) parts.add(map.get("addressLine1").toString());
            if (map.get("addressLine2") != null) parts.add(map.get("addressLine2").toString());
            if (map.get("landmark") != null) parts.add("Near " + map.get("landmark").toString());
            if (map.get("city") != null) parts.add(map.get("city").toString());
            if (map.get("state") != null) parts.add(map.get("state").toString());
            if (map.get("postalCode") != null) parts.add("PIN: " + map.get("postalCode").toString());
            else if (map.get("pinCode") != null) parts.add("PIN: " + map.get("pinCode").toString());
            return String.join(", ", parts);
        } catch (Exception e) {
            return "";
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

    public static String convertAmountToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Indian Rupees Zero Only";
        }
        long rupees = amount.longValue();
        int paise = amount.remainder(BigDecimal.ONE).multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();

        StringBuilder sb = new StringBuilder("Indian Rupees ");
        sb.append(convertWholeNumberToWords(rupees));
        if (paise > 0) {
            sb.append(" and ").append(convertWholeNumberToWords(paise)).append(" Paise");
        }
        sb.append(" Only");
        return sb.toString();
    }

    private static final String[] UNITS = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String convertWholeNumberToWords(long n) {
        if (n == 0) return "Zero";
        StringBuilder words = new StringBuilder();

        if (n >= 10000000) {
            words.append(convertWholeNumberToWords(n / 10000000)).append(" Crore ");
            n %= 10000000;
        }
        if (n >= 100000) {
            words.append(convertWholeNumberToWords(n / 100000)).append(" Lakh ");
            n %= 100000;
        }
        if (n >= 1000) {
            words.append(convertWholeNumberToWords(n / 1000)).append(" Thousand ");
            n %= 1000;
        }
        if (n >= 100) {
            words.append(convertWholeNumberToWords(n / 100)).append(" Hundred ");
            n %= 100;
        }
        if (n > 0) {
            if (n < 20) {
                words.append(UNITS[(int) n]);
            } else {
                words.append(TENS[(int) (n / 10)]);
                if (n % 10 > 0) {
                    words.append("-").append(UNITS[(int) (n % 10)]);
                }
            }
        }
        return words.toString().replaceAll("\\s+", " ").trim();
    }
}
