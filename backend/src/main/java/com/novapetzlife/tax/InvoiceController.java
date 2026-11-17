package com.novapetzlife.tax;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tax & GST Invoicing", description = "GST-compliant tax invoices, Kerala state code 32, HSN breakdown, and printable billing")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/api/v1/orders/{orderNumber}/invoice")
    @Operation(summary = "Get official GST invoice for order", description = "Retrieves itemized GST tax invoice with Kuravankonam store GSTIN, state code 32, HSN breakdown, and return policy snapshot")
    public ResponseEntity<ApiResponse<InvoiceDto>> getOrderInvoice(@PathVariable String orderNumber) {
        InvoiceDto invoice = invoiceService.getInvoiceDtoByOrderNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(invoice));
    }

    @GetMapping("/api/v1/admin/orders/{orderNumber}/invoice")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
    @Operation(summary = "Get official GST invoice (Admin)", description = "Store billing view with GST calculations for order packing and customer receipt")
    public ResponseEntity<ApiResponse<InvoiceDto>> getAdminOrderInvoice(@PathVariable String orderNumber) {
        InvoiceDto invoice = invoiceService.getInvoiceDtoByOrderNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(invoice));
    }
}
