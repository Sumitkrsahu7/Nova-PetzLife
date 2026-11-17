package com.novapetzlife.support;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.order.Order;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    @Column(nullable = false, length = 30)
    private String status = "OPEN"; // OPEN, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<SupportMessage> messages = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public SupportTicket() {}

    public SupportTicket(
            String ticketNumber,
            Customer customer,
            Order order,
            String subject,
            String priority) {
        this.ticketNumber = ticketNumber;
        this.customer = customer;
        this.order = order;
        this.subject = subject;
        this.priority = priority != null ? priority : "MEDIUM";
        this.status = "OPEN";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addMessage(SupportMessage message) {
        messages.add(message);
        message.setTicket(this);
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public List<SupportMessage> getMessages() { return messages; }
    public void setMessages(List<SupportMessage> messages) { this.messages = messages; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
