package com.novapetzlife.notification;

import com.novapetzlife.customer.Customer;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 150)
    private String recipient;

    @Column(nullable = false, length = 30)
    private String channel; // EMAIL, SMS, WHATSAPP, IN_APP

    @Column(nullable = false, length = 100)
    private String template;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, SENT, FAILED

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(
            Customer customer,
            String recipient,
            String channel,
            String template,
            String subject,
            String content,
            String status) {
        this.customer = customer;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.content = content;
        this.status = status != null ? status : "SENT";
        this.sentAt = "SENT".equals(this.status) ? Instant.now() : null;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
