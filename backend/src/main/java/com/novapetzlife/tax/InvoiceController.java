package com.novapetzlife.tax;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.common.ResourceNotFoundException;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tax & GST Invoicing", description = "GST-compliant tax invoices, Kerala state code 32, HSN breakdown, and printable billing")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final OrderRepository orderRepository;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoicePdfService invoicePdfService,
            OrderRepository orderRepository) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/api/v1/orders/{orderNumber}/invoice")
    @Operation(summary = "Get official GST invoice or provisional receipt for order", description = "Retrieves itemized GST tax invoice or provisional receipt based on store GST configuration")
    public ResponseEntity<ApiResponse<InvoiceDto>> getOrderInvoice(
            @PathVariable String orderNumber,
            Authentication authentication) {
        InvoiceDto invoice = getAuthorizedInvoice(orderNumber, authentication);
        return ResponseEntity.ok(ApiResponse.ok(invoice));
    }

    @GetMapping(value = {"/api/v1/orders/{orderNumber}/invoice/download", "/api/v1/invoices/{orderNumber}/download"}, produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download official GST invoice or provisional receipt PDF", description = "Generates and streams a clean, itemized tax invoice or provisional receipt PDF with prominent grand total")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @PathVariable String orderNumber,
            Authentication authentication) {
        InvoiceDto invoice = getAuthorizedInvoice(orderNumber, authentication);

        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String prefix = (invoice.storeGstin() != null && !invoice.storeGstin().isBlank()) ? "Invoice-" : "Receipt-";
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(prefix + invoice.invoiceNumber() + ".pdf")
                .build());
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/api/v1/admin/orders/{orderNumber}/invoice")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Get official GST invoice or provisional receipt (Admin)", description = "Store billing view with GST calculations for order packing and customer receipt")
    public ResponseEntity<ApiResponse<InvoiceDto>> getAdminOrderInvoice(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order: " + orderNumber));
        InvoiceDto invoice = invoiceService.getInvoiceDtoByOrder(order);
        return ResponseEntity.ok(ApiResponse.ok(invoice));
    }

    private InvoiceDto getAuthorizedInvoice(String orderNumber, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access invoice");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF_ADMIN") ||
                               a.getAuthority().equals("ROLE_STORE_MANAGER"));

        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);

        // Security Anti-Enumeration Oracle Protection:
        // Returns identical HTTP 404 Not Found if order does not exist OR if customer does not own the order
        if (order == null) {
            throw new ResourceNotFoundException("Invoice not found for order: " + orderNumber);
        }

        if (!isAdmin) {
            String authEmail = auth.getName();
            String ownerEmail = (order.getCustomer() != null && order.getCustomer().getEmail() != null)
                    ? order.getCustomer().getEmail()
                    : "";
            if (!ownerEmail.equalsIgnoreCase(authEmail)) {
                throw new ResourceNotFoundException("Invoice not found for order: " + orderNumber);
            }
        }

        return invoiceService.getInvoiceDtoByOrder(order);
    }
}
