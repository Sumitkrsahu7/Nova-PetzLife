package com.novapetzlife.support;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.support.dto.*;
import com.novapetzlife.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Customer Support & FAQs", description = "Store location, hours, interactive FAQs, and customer inquiry ticket submissions")
public class SupportController {

    private final SupportService supportService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public SupportController(
            SupportService supportService,
            CustomerService customerService,
            CustomerRepository customerRepository) {
        this.supportService = supportService;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    private Customer resolveCustomer(User user) {
        if (user != null) {
            return customerService.getOrCreateCustomerForUser(user);
        }
        return null;
    }

    @GetMapping("/store-info")
    @Operation(summary = "Get official store info", description = "Verified address, Kuravankonam coordinates, 20 km delivery radius, and store desk opening hours")
    public ResponseEntity<ApiResponse<StoreInfoDto>> getStoreInfo() {
        return ResponseEntity.ok(ApiResponse.ok(supportService.getStoreInfo()));
    }

    @GetMapping("/faqs")
    @Operation(summary = "Get frequently asked questions", description = "Answers on delivery zones, delivery slots, payment modes, and return policies")
    public ResponseEntity<ApiResponse<List<FaqItemDto>>> getFaqs() {
        return ResponseEntity.ok(ApiResponse.ok(supportService.getFaqs()));
    }

    @PostMapping("/inquiries")
    @Operation(summary = "Submit customer inquiry ticket", description = "Creates a new support ticket with automatic notification confirmation")
    public ResponseEntity<ApiResponse<SupportTicketDto>> submitInquiry(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateInquiryRequest request) {
        Customer customer = resolveCustomer(user);
        SupportTicketDto ticket = supportService.createInquiry(request, customer);
        return ResponseEntity.ok(ApiResponse.ok("Inquiry submitted successfully", ticket));
    }

    @GetMapping("/tickets/{ticketNumber}")
    @Operation(summary = "Track support ticket by ticket number", description = "Retrieves ticket status and entire conversation message thread")
    public ResponseEntity<ApiResponse<SupportTicketDto>> getTicket(@PathVariable String ticketNumber) {
        SupportTicketDto ticket = supportService.getTicketByNumber(ticketNumber);
        return ResponseEntity.ok(ApiResponse.ok(ticket));
    }

    @PostMapping("/tickets/{ticketNumber}/messages")
    @Operation(summary = "Reply to support ticket", description = "Appends customer reply to the open ticket thread")
    public ResponseEntity<ApiResponse<SupportTicketDto>> replyToTicket(
            @PathVariable String ticketNumber,
            @Valid @RequestBody AddMessageRequest request) {
        SupportTicketDto updated = supportService.addMessageToTicket(ticketNumber, request);
        return ResponseEntity.ok(ApiResponse.ok("Message added", updated));
    }

    @GetMapping("/my-tickets")
    @Operation(summary = "Get tickets submitted by authenticated customer", description = "List of inquiries submitted by logged-in user")
    public ResponseEntity<ApiResponse<List<SupportTicketDto>>> getMyTickets(@AuthenticationPrincipal User user) {
        Customer customer = resolveCustomer(user);
        List<SupportTicketDto> tickets = supportService.getCustomerTickets(customer);
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }
}
