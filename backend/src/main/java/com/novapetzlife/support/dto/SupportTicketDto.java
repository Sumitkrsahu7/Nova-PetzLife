package com.novapetzlife.support.dto;

import com.novapetzlife.support.SupportTicket;
import java.time.Instant;
import java.util.List;

public class SupportTicketDto {

    private Long id;
    private String ticketNumber;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String orderNumber;
    private String subject;
    private String priority;
    private String status;
    private String assignedTo;
    private List<SupportMessageDto> messages;
    private Instant createdAt;
    private Instant updatedAt;

    public SupportTicketDto() {}

    public SupportTicketDto(Long id, String ticketNumber, Long customerId, String customerName, String customerEmail, String customerPhone, String orderNumber, String subject, String priority, String status, String assignedTo, List<SupportMessageDto> messages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ticketNumber = ticketNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.orderNumber = orderNumber;
        this.subject = subject;
        this.priority = priority;
        this.status = status;
        this.assignedTo = assignedTo;
        this.messages = messages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SupportTicketDto fromEntity(SupportTicket t) {
        List<SupportMessageDto> msgDtos = t.getMessages() != null
                ? t.getMessages().stream().map(SupportMessageDto::fromEntity).toList()
                : List.of();

        return new SupportTicketDto(
                t.getId(),
                t.getTicketNumber(),
                t.getCustomer() != null ? t.getCustomer().getId() : null,
                t.getCustomer() != null ? t.getCustomer().getFirstName() + " " + t.getCustomer().getLastName() : "Customer",
                t.getCustomer() != null ? t.getCustomer().getEmail() : null,
                t.getCustomer() != null ? t.getCustomer().getPhone() : null,
                t.getOrder() != null ? t.getOrder().getOrderNumber() : null,
                t.getSubject(),
                t.getPriority(),
                t.getStatus(),
                t.getAssignedTo(),
                msgDtos,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public List<SupportMessageDto> getMessages() { return messages; }
    public void setMessages(List<SupportMessageDto> messages) { this.messages = messages; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
