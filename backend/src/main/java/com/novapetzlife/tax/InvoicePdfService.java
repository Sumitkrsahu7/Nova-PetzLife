package com.novapetzlife.tax;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;

@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);
    private static final DecimalFormat CURRENCY_FMT = new DecimalFormat("#,##0.00");

    // Brand Colors
    private static final Color BRAND_PRIMARY = new Color(27, 67, 50);      // #1b4332 Dark Forest Green
    private static final Color BRAND_ACCENT = new Color(45, 106, 79);      // #2d6a4f Emerald Green
    private static final Color TABLE_HEADER_BG = new Color(236, 243, 239); // Light green-gray
    private static final Color TABLE_BORDER = new Color(210, 220, 215);
    private static final Color GRAND_TOTAL_BG = new Color(216, 243, 220);  // Mint light accent
    private static final Color TEXT_DARK = new Color(33, 37, 41);
    private static final Color TEXT_MUTED = new Color(108, 117, 125);

    public byte[] generateInvoicePdf(InvoiceDto invoice) {
        boolean hasGstin = "GST_TAX_INVOICE".equalsIgnoreCase(invoice.documentType())
                && invoice.storeGstin() != null
                && !invoice.storeGstin().isBlank();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_PRIMARY);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND_ACCENT);
            Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, TEXT_DARK);
            Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TEXT_DARK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);
            Font grandTotalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_PRIMARY);

            // ==========================================
            // 1. TOP HEADER: STORE DETAILS & INVOICE TAG
            // ==========================================
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60f, 40f});

            // Store block
            PdfPCell storeCell = new PdfPCell();
            storeCell.setBorder(Rectangle.NO_BORDER);
            storeCell.addElement(new Paragraph(invoice.storeName() != null ? invoice.storeName() : "NOVA PETZLIFE", brandFont));
            storeCell.addElement(new Paragraph("Premium Pet Nutrition, Treats & Veterinary Healthcare", smallFont));
            storeCell.addElement(new Paragraph(invoice.storeAddress() + ", " + invoice.storeCity() + ", " + invoice.storeState() + " - " + invoice.storePinCode(), bodyFont));
            storeCell.addElement(new Paragraph("Phone: " + invoice.storePhone() + " | State: " + invoice.storeState() + " (Code: " + invoice.storeStateCode() + ")", bodyFont));
            if (hasGstin) {
                storeCell.addElement(new Paragraph("GSTIN: " + invoice.storeGstin(), bodyBold));
            } else {
                storeCell.addElement(new Paragraph("GST details are not configured. This document is not represented as a GST tax invoice.", smallFont));
            }
            headerTable.addCell(storeCell);

            // Invoice Title & Meta Block
            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

            String titleText = hasGstin ? "TAX INVOICE" : "ORDER RECEIPT — PROVISIONAL";
            Paragraph titleP = new Paragraph(titleText, titleFont);
            titleP.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(titleP);

            PdfPTable innerMeta = new PdfPTable(2);
            innerMeta.setWidthPercentage(100);
            innerMeta.setWidths(new float[]{45f, 55f});

            addMetaRow(innerMeta, (hasGstin ? "Invoice No:" : "Receipt No:"), invoice.invoiceNumber(), bodyBold, bodyFont);
            addMetaRow(innerMeta, (hasGstin ? "Invoice Date:" : "Receipt Date:"), invoice.invoiceDate() != null ? invoice.invoiceDate().toString() : "", bodyBold, bodyFont);
            addMetaRow(innerMeta, "Order No:", invoice.orderNumber(), bodyBold, bodyFont);
            addMetaRow(innerMeta, "Order Date:", invoice.orderDate() != null ? invoice.orderDate() : "", bodyBold, bodyFont);
            addMetaRow(innerMeta, "Payment:", (invoice.paymentMethod() != null ? invoice.paymentMethod() : "ONLINE") + " (" + (invoice.paymentStatus() != null ? invoice.paymentStatus() : "PAID") + ")", bodyBold, bodyFont);
            if (hasGstin) {
                addMetaRow(innerMeta, "Place of Supply:", invoice.placeOfSupply() != null ? invoice.placeOfSupply() : "Kerala (32)", bodyBold, bodyFont);
            }

            metaCell.addElement(innerMeta);
            headerTable.addCell(metaCell);
            document.add(headerTable);

            // Separator Line
            document.add(new Paragraph(" "));
            PdfPTable line = new PdfPTable(1);
            line.setWidthPercentage(100);
            PdfPCell lineCell = new PdfPCell();
            lineCell.setBorder(Rectangle.BOTTOM);
            lineCell.setBorderColor(BRAND_PRIMARY);
            lineCell.setBorderWidth(1.5f);
            line.addCell(lineCell);
            document.add(line);
            document.add(new Paragraph(" "));

            // ==========================================
            // 2. CUSTOMER & BILL TO / SHIP TO
            // ==========================================
            PdfPTable customerTable = new PdfPTable(2);
            customerTable.setWidthPercentage(100);
            customerTable.setWidths(new float[]{50f, 50f});

            PdfPCell billToCell = new PdfPCell();
            billToCell.setBorder(Rectangle.NO_BORDER);
            billToCell.addElement(new Paragraph("BILL TO & DISPATCH TO:", subHeaderFont));
            billToCell.addElement(new Paragraph(invoice.customerName() != null ? invoice.customerName() : "Valued Customer", bodyBold));
            if (invoice.customerPhone() != null && !invoice.customerPhone().isBlank()) {
                billToCell.addElement(new Paragraph("Phone: " + invoice.customerPhone(), bodyFont));
            }
            if (invoice.customerEmail() != null && !invoice.customerEmail().isBlank()) {
                billToCell.addElement(new Paragraph("Email: " + invoice.customerEmail(), bodyFont));
            }
            billToCell.addElement(new Paragraph("Address: " + (invoice.deliveryAddress() != null ? invoice.deliveryAddress() : "Local Delivery, Thiruvananthapuram"), bodyFont));
            customerTable.addCell(billToCell);

            PdfPCell deliveryMetaCell = new PdfPCell();
            deliveryMetaCell.setBorder(Rectangle.NO_BORDER);
            deliveryMetaCell.addElement(new Paragraph("DELIVERY DETAILS:", subHeaderFont));
            deliveryMetaCell.addElement(new Paragraph("Delivery Mode: Express Local Store Dispatch (20km radius)", bodyFont));
            deliveryMetaCell.addElement(new Paragraph("Delivery Window: " + (invoice.deliverySlot() != null ? invoice.deliverySlot() : "Standard Daytime Delivery"), bodyFont));
            if (hasGstin) {
                deliveryMetaCell.addElement(new Paragraph("Reverse Charge: " + (invoice.reverseCharge() != null ? invoice.reverseCharge() : "No"), bodyFont));
            }
            deliveryMetaCell.addElement(new Paragraph("Jurisdiction: Thiruvananthapuram, Kerala", bodyFont));
            customerTable.addCell(deliveryMetaCell);

            document.add(customerTable);
            document.add(new Paragraph(" "));

            // ==========================================
            // 3. ITEM TABLE
            // ==========================================
            if (hasGstin) {
                // 8 columns for configured GST tax invoice
                PdfPTable itemTable = new PdfPTable(8);
                itemTable.setWidthPercentage(100);
                itemTable.setWidths(new float[]{6f, 32f, 12f, 8f, 11f, 10f, 10f, 11f});

                addTableHeader(itemTable, "#", subHeaderFont);
                addTableHeader(itemTable, "Description of Goods", subHeaderFont);
                addTableHeader(itemTable, "HSN Code", subHeaderFont);
                addTableHeader(itemTable, "Qty", subHeaderFont);
                addTableHeader(itemTable, "Unit Rate", subHeaderFont);
                addTableHeader(itemTable, "Taxable", subHeaderFont);
                addTableHeader(itemTable, "GST", subHeaderFont);
                addTableHeader(itemTable, "Total", subHeaderFont);

                if (invoice.items() != null && !invoice.items().isEmpty()) {
                    for (InvoiceDto.InvoiceItemDto item : invoice.items()) {
                        addTableCell(itemTable, String.valueOf(item.itemNo()), Element.ALIGN_CENTER, bodyFont);

                        Paragraph prodP = new Paragraph(item.productName(), bodyBold);
                        if (item.sku() != null) prodP.add(new Chunk("\nSKU: " + item.sku(), smallFont));
                        if (item.returnPolicy() != null) prodP.add(new Chunk("\nPolicy: " + item.returnPolicy(), smallFont));
                        PdfPCell pCell = new PdfPCell(prodP);
                        pCell.setPadding(5);
                        pCell.setBorderColor(TABLE_BORDER);
                        itemTable.addCell(pCell);

                        String hsn = (item.hsnCode() != null && !item.hsnCode().isBlank()) ? item.hsnCode() : "-";
                        addTableCell(itemTable, hsn, Element.ALIGN_CENTER, bodyFont);
                        addTableCell(itemTable, String.valueOf(item.quantity()), Element.ALIGN_CENTER, bodyFont);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(item.unitPrice()), Element.ALIGN_RIGHT, bodyFont);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(item.taxableValue()), Element.ALIGN_RIGHT, bodyFont);

                        BigDecimal totalGst = (item.cgstAmount() != null ? item.cgstAmount() : BigDecimal.ZERO)
                                .add(item.sgstAmount() != null ? item.sgstAmount() : BigDecimal.ZERO);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(totalGst), Element.ALIGN_RIGHT, bodyFont);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(item.totalAmount()), Element.ALIGN_RIGHT, bodyBold);
                    }
                }
                document.add(itemTable);
            } else {
                // 6 columns for provisional receipt — strictly without fabricated tax breakdowns
                PdfPTable itemTable = new PdfPTable(6);
                itemTable.setWidthPercentage(100);
                itemTable.setWidths(new float[]{6f, 46f, 12f, 10f, 13f, 13f});

                addTableHeader(itemTable, "#", subHeaderFont);
                addTableHeader(itemTable, "Description of Goods", subHeaderFont);
                addTableHeader(itemTable, "HSN Code", subHeaderFont);
                addTableHeader(itemTable, "Qty", subHeaderFont);
                addTableHeader(itemTable, "Unit Rate", subHeaderFont);
                addTableHeader(itemTable, "Total (₹)", subHeaderFont);

                if (invoice.items() != null && !invoice.items().isEmpty()) {
                    for (InvoiceDto.InvoiceItemDto item : invoice.items()) {
                        addTableCell(itemTable, String.valueOf(item.itemNo()), Element.ALIGN_CENTER, bodyFont);

                        Paragraph prodP = new Paragraph(item.productName(), bodyBold);
                        if (item.sku() != null) prodP.add(new Chunk("\nSKU: " + item.sku(), smallFont));
                        if (item.returnPolicy() != null) prodP.add(new Chunk("\nPolicy: " + item.returnPolicy(), smallFont));
                        PdfPCell pCell = new PdfPCell(prodP);
                        pCell.setPadding(5);
                        pCell.setBorderColor(TABLE_BORDER);
                        itemTable.addCell(pCell);

                        String hsn = (item.hsnCode() != null && !item.hsnCode().isBlank()) ? item.hsnCode() : "-";
                        addTableCell(itemTable, hsn, Element.ALIGN_CENTER, bodyFont);
                        addTableCell(itemTable, String.valueOf(item.quantity()), Element.ALIGN_CENTER, bodyFont);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(item.unitPrice()), Element.ALIGN_RIGHT, bodyFont);
                        addTableCell(itemTable, "₹" + CURRENCY_FMT.format(item.totalAmount()), Element.ALIGN_RIGHT, bodyBold);
                    }
                }
                document.add(itemTable);
            }

            document.add(new Paragraph(" "));

            // ==========================================
            // 4. FINANCIAL SUMMARY & PROMINENT GRAND TOTAL
            // ==========================================
            PdfPTable bottomTable = new PdfPTable(2);
            bottomTable.setWidthPercentage(100);
            bottomTable.setWidths(new float[]{55f, 45f});

            // Left: Words & Terms
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("AMOUNT IN WORDS:", subHeaderFont));
            leftCell.addElement(new Paragraph(invoice.amountInWords() != null ? invoice.amountInWords() : "Indian Rupees Only", bodyBold));

            Paragraph termsTitle = new Paragraph("\nTerms & Conditions:", subHeaderFont);
            leftCell.addElement(termsTitle);
            String term4 = hasGstin
                    ? "4. This is a computer generated authentic GST Tax Invoice."
                    : "4. GST details are not configured. This document is not represented as a GST tax invoice.";

            Paragraph termsBody = new Paragraph(
                    "1. Goods once sold are covered under the item-level return policy indicated above.\n" +
                    "2. Defective or damaged items must be reported within 48 hours with unboxing video/photo.\n" +
                    "3. Subject to Thiruvananthapuram, Kerala jurisdiction only.\n" +
                    term4,
                    smallFont
            );
            leftCell.addElement(termsBody);
            bottomTable.addCell(leftCell);

            // Right: Calculation Summary Table
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{60f, 40f});

            if (hasGstin) {
                addCalcRow(summaryTable, "Taxable Subtotal:", "₹" + CURRENCY_FMT.format(invoice.totalTaxableValue() != null ? invoice.totalTaxableValue() : BigDecimal.ZERO), bodyFont, bodyFont);
                addCalcRow(summaryTable, "Central GST (CGST 9%):", "₹" + CURRENCY_FMT.format(invoice.totalCgst() != null ? invoice.totalCgst() : BigDecimal.ZERO), bodyFont, bodyFont);
                addCalcRow(summaryTable, "State GST (SGST 9%):", "₹" + CURRENCY_FMT.format(invoice.totalSgst() != null ? invoice.totalSgst() : BigDecimal.ZERO), bodyFont, bodyFont);
            } else {
                addCalcRow(summaryTable, "Item Subtotal:", "₹" + CURRENCY_FMT.format(invoice.totalTaxableValue() != null ? invoice.totalTaxableValue() : BigDecimal.ZERO), bodyFont, bodyFont);
            }

            if (invoice.discountAmount() != null && invoice.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addCalcRow(summaryTable, "Discount:", "-₹" + CURRENCY_FMT.format(invoice.discountAmount()), bodyFont, bodyFont);
            }

            addCalcRow(summaryTable, "Delivery & Handling:", "₹" + CURRENCY_FMT.format(invoice.deliveryCharge() != null ? invoice.deliveryCharge() : BigDecimal.ZERO), bodyFont, bodyFont);

            // PROMINENT GRAND TOTAL ROW
            PdfPCell gtLabel = new PdfPCell(new Paragraph("GRAND TOTAL:", grandTotalFont));
            gtLabel.setBackgroundColor(GRAND_TOTAL_BG);
            gtLabel.setBorder(Rectangle.BOX);
            gtLabel.setBorderColor(BRAND_PRIMARY);
            gtLabel.setBorderWidth(1.5f);
            gtLabel.setPadding(8);
            summaryTable.addCell(gtLabel);

            PdfPCell gtVal = new PdfPCell(new Paragraph("₹" + CURRENCY_FMT.format(invoice.grandTotal() != null ? invoice.grandTotal() : BigDecimal.ZERO), grandTotalFont));
            gtVal.setBackgroundColor(GRAND_TOTAL_BG);
            gtVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gtVal.setBorder(Rectangle.BOX);
            gtVal.setBorderColor(BRAND_PRIMARY);
            gtVal.setBorderWidth(1.5f);
            gtVal.setPadding(8);
            summaryTable.addCell(gtVal);

            rightCell.addElement(summaryTable);

            // Signatory box
            Paragraph sigP = new Paragraph("\nFor NOVA PETZLIFE\n\n\nAuthorised Signatory / Computer Generated", smallFont);
            sigP.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(sigP);

            bottomTable.addCell(rightCell);
            document.add(bottomTable);

            document.close();
            log.info("Generated PDF for order {}: {} bytes (hasGstin: {})", invoice.orderNumber(), out.size(), hasGstin);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for invoice {}: {}", invoice.invoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF invoice: " + e.getMessage(), e);
        }
    }

    private void addMetaRow(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell lCell = new PdfPCell(new Paragraph(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lCell.setPadding(1);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Paragraph(value != null ? value : "", valFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPadding(1);
        table.addCell(vCell);
    }

    private void addTableHeader(PdfPTable table, String title, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(title, font));
        cell.setBackgroundColor(TABLE_HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        cell.setBorderColor(TABLE_BORDER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        cell.setBorderColor(TABLE_BORDER);
        table.addCell(cell);
    }

    private void addCalcRow(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell lCell = new PdfPCell(new Paragraph(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(3);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Paragraph(value, valFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPadding(3);
        table.addCell(vCell);
    }
}
