package com.novapetzlife.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderItem;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.order.OrderStatusHistory;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.PaymentRepository;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.refund.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;

@Service
public class SalesReportService {

    private static final Logger log = LoggerFactory.getLogger(SalesReportService.class);
    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final DecimalFormat CURRENCY_FMT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DISPLAY_DT_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZONE_KOLKATA);
    private static final DateTimeFormatter RANGE_DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZONE_KOLKATA);

    private static final Color BRAND_PRIMARY = new Color(27, 67, 50);      // #1b4332
    private static final Color BRAND_ACCENT = new Color(45, 106, 79);      // #2d6a4f
    private static final Color CARD_BG = new Color(243, 247, 244);
    private static final Color CARD_BORDER = new Color(200, 218, 208);
    private static final Color TABLE_HEADER_BG = new Color(230, 240, 234);
    private static final Color TABLE_BORDER = new Color(215, 225, 220);
    private static final Color TEXT_DARK = new Color(33, 37, 41);
    private static final Color TEXT_MUTED = new Color(108, 117, 125);

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public SalesReportService(
            OrderRepository orderRepository,
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SalesReportSummaryDto generateSalesReport(String periodStr, String dateStr) {
        return generateSalesReport(periodStr, dateStr, null, null);
    }

    @Transactional(readOnly = true)
    public SalesReportSummaryDto generateSalesReport(String periodStr, String dateStr, String startDateStr, String endDateStr) {
        String normalizedPeriod = (periodStr != null && !periodStr.isBlank()) ? periodStr.trim().toUpperCase() : "WEEKLY";
        LocalDate baseDate = parseBaseDate(dateStr);

        ZonedDateTime startZdt;
        ZonedDateTime endZdt;

        switch (normalizedPeriod) {
            case "CUSTOM" -> {
                if (!StringUtils.hasText(startDateStr) || !StringUtils.hasText(endDateStr)) {
                    throw new IllegalArgumentException("Both startDate and endDate are required for CUSTOM report period");
                }
                LocalDate customStart = parseBaseDate(startDateStr);
                LocalDate customEnd = parseBaseDate(endDateStr);
                if (customEnd.isBefore(customStart)) {
                    throw new IllegalArgumentException("End date cannot be earlier than start date: " + endDateStr + " < " + startDateStr);
                }
                if (customStart.plusYears(5).isBefore(customEnd)) {
                    throw new IllegalArgumentException("Custom date range cannot exceed 5 years");
                }
                startZdt = customStart.atStartOfDay(ZONE_KOLKATA);
                endZdt = customEnd.atTime(23, 59, 59, 999_999_999).atZone(ZONE_KOLKATA);
            }
            case "YEARLY" -> {
                // Indian Financial Year: April 1 to March 31
                int year = baseDate.getYear();
                int fyStartYear = baseDate.getMonthValue() >= 4 ? year : year - 1;
                LocalDate fyStart = LocalDate.of(fyStartYear, 4, 1);
                LocalDate fyEnd = LocalDate.of(fyStartYear + 1, 3, 31);
                startZdt = fyStart.atStartOfDay(ZONE_KOLKATA);
                endZdt = fyEnd.atTime(23, 59, 59, 999_999_999).atZone(ZONE_KOLKATA);
            }
            case "MONTHLY" -> {
                startZdt = baseDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZONE_KOLKATA);
                endZdt = baseDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59, 999_999_999).atZone(ZONE_KOLKATA);
            }
            case "WEEKLY" -> {
                // ISO-8601 week: Monday to Sunday
                LocalDate monday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                startZdt = monday.atStartOfDay(ZONE_KOLKATA);
                endZdt = sunday.atTime(23, 59, 59, 999_999_999).atZone(ZONE_KOLKATA);
            }
            default -> throw new IllegalArgumentException("Invalid report period: " + periodStr + ". Must be WEEKLY, MONTHLY, YEARLY, or CUSTOM.");
        }

        Instant startInstant = startZdt.toInstant();
        Instant endInstant = endZdt.toInstant();

        // TASK 3: Sales Report Payment-Date Attribution
        // Candidates queried by paidAt window AND createdAt window to capture date-attributed payments
        // and in-period created orders (pending/cancelled/legacy).
        List<Order> paidAtOrders = orderRepository.findByPaidAtBetweenOrderByPaidAtDesc(startInstant, endInstant);
        List<Order> createdAtOrders = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startInstant, endInstant);

        Map<Long, Order> candidateMap = new LinkedHashMap<>();
        if (paidAtOrders != null) {
            for (Order o : paidAtOrders) {
                candidateMap.put(o.getId(), o);
            }
        }
        if (createdAtOrders != null) {
            for (Order o : createdAtOrders) {
                candidateMap.putIfAbsent(o.getId(), o);
            }
        }
        List<Payment> periodPayments = paymentRepository.findByCompletedAtBetweenAndStatusIn(
                startInstant,
                endInstant,
                List.of("SUCCESS", "COMPLETED")
        );
        if (periodPayments != null) {
            for (Payment p : periodPayments) {
                if (p.getOrder() != null && !candidateMap.containsKey(p.getOrder().getId())) {
                    candidateMap.put(p.getOrder().getId(), p.getOrder());
                }
            }
        }

        // Period-based refund attribution: refunds successfully completed in this period (using completedAt)
        List<Refund> periodRefunds = refundRepository.findByCompletedAtBetweenAndStatusInOrderByCompletedAtDesc(
                startInstant,
                endInstant,
                List.of("SUCCESS", "COMPLETED", "PROCESSED")
        );
        BigDecimal totalRefunds = BigDecimal.ZERO;
        if (periodRefunds != null) {
            for (Refund r : periodRefunds) {
                totalRefunds = totalRefunds.add(r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO);
            }
        }

        long successfulOrders = 0;
        long pendingOrders = 0;
        long cancelledOrders = 0;
        long refundedOrders = 0;
        long failedOrders = 0;
        long unattributableOrdersCount = 0;
        BigDecimal unattributableAmount = BigDecimal.ZERO;

        BigDecimal itemSubtotal = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal totalShipping = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal customerReceipts = BigDecimal.ZERO;
        long totalUnitsSold = 0;

        List<SalesReportOrderDto> orderDtos = new ArrayList<>();

        for (Order order : candidateMap.values()) {
            String status = order.getOrderStatus() != null ? order.getOrderStatus().toUpperCase() : "UNKNOWN";
            String pStatus = order.getPaymentStatus() != null ? order.getPaymentStatus().toUpperCase() : "PENDING";

            boolean isCancelled = "CANCELLED".equals(status);
            boolean isFailed = "FAILED".equals(pStatus) || "FAILED".equals(status);
            boolean isRefunded = "REFUNDED".equals(pStatus) || "REFUNDED".equals(status);
            boolean isPartiallyRefunded = "PARTIALLY_REFUNDED".equals(pStatus);
            boolean isPending = "PENDING".equals(pStatus) || "PENDING_PAYMENT".equals(status);
            boolean isCompleted = "COMPLETED".equals(pStatus) || "PAID".equals(pStatus) || "SUCCESS".equals(pStatus);

            boolean isEligiblePaidStatus = (isCompleted || isPartiallyRefunded || isRefunded) && !isCancelled && !isFailed;

            if (isCancelled) {
                if (order.getCreatedAt() != null && !order.getCreatedAt().isBefore(startInstant) && !order.getCreatedAt().isAfter(endInstant)) {
                    cancelledOrders++;
                }
                continue;
            }
            if (isFailed) {
                if (order.getCreatedAt() != null && !order.getCreatedAt().isBefore(startInstant) && !order.getCreatedAt().isAfter(endInstant)) {
                    failedOrders++;
                }
                continue;
            }
            if (isPending) {
                if (order.getCreatedAt() != null && !order.getCreatedAt().isBefore(startInstant) && !order.getCreatedAt().isAfter(endInstant)) {
                    pendingOrders++;
                }
                continue;
            }

            if (isEligiblePaidStatus) {
                Instant paymentCompletionTime = resolvePaymentTimestamp(order);

                // Exclude un-attributable records where payment completion timestamp cannot be verified
                if (paymentCompletionTime == null) {
                    unattributableOrdersCount++;
                    BigDecimal unattrVal = order.getFinalPayableAmount() != null ? order.getFinalPayableAmount() : BigDecimal.ZERO;
                    unattributableAmount = unattributableAmount.add(unattrVal);
                    log.warn("Excluding un-attributable paid order {} (amount ₹{}) from date-attributed sales report",
                            order.getOrderNumber(), unattrVal);
                    continue;
                }

                // If payment was completed outside this reporting window, exclude from this period's attribution
                if (paymentCompletionTime.isBefore(startInstant) || paymentCompletionTime.isAfter(endInstant)) {
                    continue;
                }

                if (isRefunded) {
                    refundedOrders++;
                    successfulOrders++;
                } else {
                    successfulOrders++;
                }

                // Fetch order-level refunds for display in transaction table
                BigDecimal orderRefundTotal = BigDecimal.ZERO;
                List<Refund> refunds = refundRepository.findByOrderId(order.getId());
                if (refunds != null && !refunds.isEmpty()) {
                    for (Refund r : refunds) {
                        String rStatus = r.getStatus() != null ? r.getStatus().toUpperCase() : "";
                        if (("SUCCESS".equals(rStatus) || "COMPLETED".equals(rStatus) || "PROCESSED".equals(rStatus))
                                && r.getCompletedAt() != null) {
                            orderRefundTotal = orderRefundTotal.add(r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO);
                        }
                    }
                }

                BigDecimal orderItemSubtotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal orderDiscount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal orderShipping = order.getDeliveryCharge() != null ? order.getDeliveryCharge() : BigDecimal.ZERO;
                BigDecimal orderTax = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
                BigDecimal orderPaid = order.getFinalPayableAmount() != null ? order.getFinalPayableAmount() : BigDecimal.ZERO;

                itemSubtotal = itemSubtotal.add(orderItemSubtotal);
                totalDiscounts = totalDiscounts.add(orderDiscount);
                totalShipping = totalShipping.add(orderShipping);
                totalTax = totalTax.add(orderTax);
                customerReceipts = customerReceipts.add(orderPaid);

                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        totalUnitsSold += (item.getQuantity() != null ? item.getQuantity() : 0);
                    }
                }

                // Customer data extraction
                String cName = "Customer";
                String cEmail = "";
                String cPhone = "";
                if (order.getCustomer() != null) {
                    cName = (order.getCustomer().getFirstName() + " " + (order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "")).trim();
                    cEmail = order.getCustomer().getEmail() != null ? order.getCustomer().getEmail() : "";
                    cPhone = order.getCustomer().getPhone() != null ? order.getCustomer().getPhone() : "";
                }

                // Shipping destination parsing from customer's delivery address (not store location)
                String shippingCity = "";
                String shippingPincode = "";
                if (StringUtils.hasText(order.getDeliveryAddressJson())) {
                    try {
                        JsonNode addrNode = objectMapper.readTree(order.getDeliveryAddressJson());
                        if (addrNode.has("city") && !addrNode.get("city").isNull()) {
                            shippingCity = addrNode.get("city").asText().trim();
                        }
                        if (addrNode.has("postalCode") && !addrNode.get("postalCode").isNull()) {
                            shippingPincode = addrNode.get("postalCode").asText().trim();
                        } else if (addrNode.has("pinCode") && !addrNode.get("pinCode").isNull()) {
                            shippingPincode = addrNode.get("pinCode").asText().trim();
                        } else if (addrNode.has("pincode") && !addrNode.get("pincode").isNull()) {
                            shippingPincode = addrNode.get("pincode").asText().trim();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse delivery address for order {}: {}", order.getOrderNumber(), e.getMessage());
                    }
                }

                // Items summary string
                StringBuilder itemsSummary = new StringBuilder();
                int itemsCount = 0;
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        itemsCount += (item.getQuantity() != null ? item.getQuantity() : 0);
                        if (itemsSummary.length() > 0) itemsSummary.append("; ");
                        itemsSummary.append(item.getProduct() != null ? item.getProduct().getName() : "Item")
                                .append(" (x").append(item.getQuantity()).append(")");
                    }
                }

                BigDecimal grandTotal = order.getFinalPayableAmount() != null ? order.getFinalPayableAmount() : BigDecimal.ZERO;
                BigDecimal netAmount = grandTotal.subtract(orderRefundTotal);

                orderDtos.add(new SalesReportOrderDto(
                        order.getId(),
                        order.getOrderNumber(),
                        paymentCompletionTime,
                        DISPLAY_DT_FMT.format(paymentCompletionTime),
                        cName,
                        cEmail,
                        cPhone,
                        status,
                        pStatus,
                        order.getPaymentMethod() != null ? order.getPaymentMethod() : "ONLINE",
                        itemsCount,
                        order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                        order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO,
                        order.getDeliveryCharge() != null ? order.getDeliveryCharge() : BigDecimal.ZERO,
                        order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO,
                        grandTotal,
                        orderRefundTotal,
                        netAmount,
                        shippingCity,
                        shippingPincode,
                        itemsSummary.toString()
                ));
            }
        }

        long totalOrders = successfulOrders + pendingOrders + cancelledOrders + failedOrders;
        BigDecimal netReceipts = customerReceipts.subtract(totalRefunds);
        BigDecimal averageOrderValue = successfulOrders > 0
                ? customerReceipts.divide(BigDecimal.valueOf(successfulOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String formattedRange = RANGE_DT_FMT.format(startZdt) + " — " + RANGE_DT_FMT.format(endZdt);

        return new SalesReportSummaryDto(
                normalizedPeriod,
                baseDate.toString(),
                startInstant,
                endInstant,
                formattedRange,
                totalOrders,
                successfulOrders,
                pendingOrders,
                cancelledOrders,
                refundedOrders,
                failedOrders,
                itemSubtotal.setScale(2, RoundingMode.HALF_UP),
                totalDiscounts.setScale(2, RoundingMode.HALF_UP),
                totalShipping.setScale(2, RoundingMode.HALF_UP),
                totalTax.setScale(2, RoundingMode.HALF_UP),
                customerReceipts.setScale(2, RoundingMode.HALF_UP),
                totalRefunds.setScale(2, RoundingMode.HALF_UP),
                netReceipts.setScale(2, RoundingMode.HALF_UP),
                totalUnitsSold,
                averageOrderValue.setScale(2, RoundingMode.HALF_UP),
                orderDtos,
                unattributableOrdersCount,
                unattributableAmount.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public byte[] generateCsvReport(SalesReportSummaryDto report) {
        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel auto-detection
        csv.append('\ufeff');

        // Header Metadata
        csv.append("# NOVA PETZLIFE SALES & FINANCIAL REPORT\n");
        csv.append("# Period: ").append(report.period()).append(" (").append(report.formattedDateRange()).append(")\n");
        if (report.unattributableOrdersCount() > 0) {
            csv.append("# Un-attributable Paid Records Excluded: ").append(report.unattributableOrdersCount())
                    .append(" (Total Value: ₹").append(report.unattributableAmount()).append(")\n");
        }
        csv.append("# Customer Receipts (Paid): ₹").append(report.customerReceipts()).append(", Net Receipts: ₹").append(report.netReceipts()).append("\n");
        csv.append("# Item Subtotal (Pre-Discount): ₹").append(report.itemSubtotal()).append(", Total Discounts: ₹").append(report.totalDiscounts()).append(", Delivery: ₹").append(report.totalShipping()).append("\n");
        csv.append("# Total Orders: ").append(report.totalOrders()).append(", Successful: ").append(report.successfulOrders()).append(", Units Sold: ").append(report.totalUnitsSold()).append("\n\n");

        // Columns
        csv.append("Order ID,Order Number,Order Date & Time,Customer Name,Customer Email,Customer Phone,Order Status,Payment Status,Payment Method,Units Sold,Subtotal (₹),Discount (₹),Shipping (₹),Tax (₹),Grand Total (₹),Refund (₹),Net Amount (₹),City,Pincode,Items Detail\n");

        if (report.orders() != null) {
            for (SalesReportOrderDto o : report.orders()) {
                csv.append(escapeCsv(String.valueOf(o.orderId()))).append(",")
                        .append(escapeCsv(o.orderNumber())).append(",")
                        .append(escapeCsv(o.formattedDate())).append(",")
                        .append(escapeCsv(o.customerName())).append(",")
                        .append(escapeCsv(o.customerEmail())).append(",")
                        .append(escapeCsv(o.customerPhone())).append(",")
                        .append(escapeCsv(o.orderStatus())).append(",")
                        .append(escapeCsv(o.paymentStatus())).append(",")
                        .append(escapeCsv(o.paymentMethod())).append(",")
                        .append(o.itemsCount()).append(",")
                        .append(o.subtotal()).append(",")
                        .append(o.discountAmount()).append(",")
                        .append(o.shippingAmount()).append(",")
                        .append(o.taxAmount()).append(",")
                        .append(o.grandTotal()).append(",")
                        .append(o.refundAmount()).append(",")
                        .append(o.netAmount()).append(",")
                        .append(escapeCsv(o.shippingCity())).append(",")
                        .append(escapeCsv(o.shippingPincode())).append(",")
                        .append(escapeCsv(o.itemsSummary())).append("\n");
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdfReport(SalesReportSummaryDto report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // A4 Landscape for financial tables
            Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND_PRIMARY);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK);
            Font cardTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_MUTED);
            Font cardValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_PRIMARY);
            Font tableHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_DARK);
            Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_DARK);
            Font tableBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, TEXT_DARK);

            // Document Header
            Paragraph titleP = new Paragraph("NOVA PETZLIFE — " + report.period() + " SALES REPORT", titleFont);
            Paragraph subP = new Paragraph("Reporting Period: " + report.formattedDateRange() + " | Generated on: " + DISPLAY_DT_FMT.format(Instant.now()), subtitleFont);
            document.add(titleP);
            document.add(subP);
            document.add(new Paragraph(" "));

            // ==========================================
            // KPI Summary Cards Grid (6 cards)
            // ==========================================
            PdfPTable kpiGrid = new PdfPTable(6);
            kpiGrid.setWidthPercentage(100);

            addKpiCard(kpiGrid, "CUSTOMER RECEIPTS", "₹" + CURRENCY_FMT.format(report.customerReceipts()), cardTitleFont, cardValFont);
            addKpiCard(kpiGrid, "NET RECEIPTS", "₹" + CURRENCY_FMT.format(report.netReceipts()), cardTitleFont, cardValFont);
            addKpiCard(kpiGrid, "ORDERS (PAID / TOT)", report.successfulOrders() + " / " + report.totalOrders(), cardTitleFont, cardValFont);
            addKpiCard(kpiGrid, "UNITS SOLD", String.valueOf(report.totalUnitsSold()), cardTitleFont, cardValFont);
            addKpiCard(kpiGrid, "TOTAL REFUNDS", "₹" + CURRENCY_FMT.format(report.totalRefunds()), cardTitleFont, cardValFont);
            addKpiCard(kpiGrid, "AVG ORDER VALUE", "₹" + CURRENCY_FMT.format(report.averageOrderValue()), cardTitleFont, cardValFont);

            document.add(kpiGrid);
            document.add(new Paragraph(" "));

            if (report.unattributableOrdersCount() > 0) {
                Paragraph unattrP = new Paragraph("Note: " + report.unattributableOrdersCount() + " paid order(s) totaling ₹"
                        + CURRENCY_FMT.format(report.unattributableAmount())
                        + " were excluded from this date-attributed report due to unresolvable payment timestamps.",
                        FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED));
                document.add(unattrP);
                document.add(new Paragraph(" "));
            }

            // ==========================================
            // Detailed Orders Listing
            // ==========================================
            Paragraph tableTitle = new Paragraph("Order Transactions (" + (report.orders() != null ? report.orders().size() : 0) + " records):", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_ACCENT));
            document.add(tableTitle);
            document.add(new Paragraph(" "));

            PdfPTable orderTable = new PdfPTable(10);
            orderTable.setWidthPercentage(100);
            orderTable.setWidths(new float[]{14f, 13f, 16f, 10f, 9f, 6f, 8f, 7f, 7f, 10f});

            addTableHeader(orderTable, "Order No", tableHeadFont);
            addTableHeader(orderTable, "Date & Time", tableHeadFont);
            addTableHeader(orderTable, "Customer", tableHeadFont);
            addTableHeader(orderTable, "Status", tableHeadFont);
            addTableHeader(orderTable, "Payment", tableHeadFont);
            addTableHeader(orderTable, "Units", tableHeadFont);
            addTableHeader(orderTable, "Subtotal", tableHeadFont);
            addTableHeader(orderTable, "Discount", tableHeadFont);
            addTableHeader(orderTable, "Delivery", tableHeadFont);
            addTableHeader(orderTable, "Grand Total", tableHeadFont);

            if (report.orders() != null) {
                for (SalesReportOrderDto o : report.orders()) {
                    addTableCell(orderTable, o.orderNumber(), Element.ALIGN_LEFT, tableBoldFont);
                    addTableCell(orderTable, o.formattedDate(), Element.ALIGN_CENTER, tableBodyFont);
                    addTableCell(orderTable, o.customerName(), Element.ALIGN_LEFT, tableBodyFont);
                    addTableCell(orderTable, o.orderStatus(), Element.ALIGN_CENTER, tableBodyFont);
                    addTableCell(orderTable, o.paymentStatus(), Element.ALIGN_CENTER, tableBodyFont);
                    addTableCell(orderTable, String.valueOf(o.itemsCount()), Element.ALIGN_CENTER, tableBodyFont);
                    addTableCell(orderTable, "₹" + CURRENCY_FMT.format(o.subtotal()), Element.ALIGN_RIGHT, tableBodyFont);
                    addTableCell(orderTable, "₹" + CURRENCY_FMT.format(o.discountAmount()), Element.ALIGN_RIGHT, tableBodyFont);
                    addTableCell(orderTable, "₹" + CURRENCY_FMT.format(o.shippingAmount()), Element.ALIGN_RIGHT, tableBodyFont);
                    addTableCell(orderTable, "₹" + CURRENCY_FMT.format(o.grandTotal()), Element.ALIGN_RIGHT, tableBoldFont);
                }
            }

            document.add(orderTable);
            document.close();
            log.info("Generated PDF sales report: {} bytes", out.size());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF sales report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF sales report: " + e.getMessage(), e);
        }
    }

    private void addKpiCard(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CARD_BG);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(1f);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p1 = new Paragraph(label, labelFont);
        p1.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);

        Paragraph p2 = new Paragraph(value, valFont);
        p2.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p2);

        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String title, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(title, font));
        cell.setBackgroundColor(TABLE_HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4);
        cell.setBorderColor(TABLE_BORDER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        cell.setBorderColor(TABLE_BORDER);
        table.addCell(cell);
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    private LocalDate parseBaseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return LocalDate.now(ZONE_KOLKATA);
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            log.warn("Invalid date string '{}', falling back to today", dateStr);
            return LocalDate.now(ZONE_KOLKATA);
        }
    }

    public Instant resolvePaymentTimestamp(Order order) {
        if (order == null) return null;

        String pStatus = order.getPaymentStatus() != null ? order.getPaymentStatus().toUpperCase() : "";
        boolean isPaid = "COMPLETED".equals(pStatus) || "PAID".equals(pStatus) || "SUCCESS".equals(pStatus)
                || "PARTIALLY_REFUNDED".equals(pStatus) || "REFUNDED".equals(pStatus);
        if (!isPaid) {
            return null;
        }

        // 1. Authoritative paidAt field on Order (explicitly recorded payment completion/cash collection timestamp)
        if (order.getPaidAt() != null) {
            return order.getPaidAt();
        }

        // 2. Authoritative payment gateway completion timestamp on associated Payment (e.g. Razorpay)
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        if (payments != null && !payments.isEmpty()) {
            for (Payment p : payments) {
                if (("SUCCESS".equalsIgnoreCase(p.getStatus()) || "COMPLETED".equalsIgnoreCase(p.getStatus()))
                        && p.getCompletedAt() != null) {
                    return p.getCompletedAt();
                }
            }
        }

        // Note: For COD / OFFLINE orders, DELIVERED status alone is NOT proof of cash collection.
        // Unrelated Payment updates (updatedAt) and creation timestamps (createdAt) must never be used.
        // Without an explicit persisted completion timestamp, return null so it is audited as unattributable.
        return null;
    }
}
