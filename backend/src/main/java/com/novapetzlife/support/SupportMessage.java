package com.novapetzlife.support;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType; // CUSTOMER, STAFF

    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public SupportMessage() {}

    public SupportMessage(
            SupportTicket ticket,
            String senderType,
            String senderId,
            String message,
            String attachmentUrl) {
        this.ticket = ticket;
        this.senderType = senderType;
        this.senderId = senderId;
        this.message = message;
        this.attachmentUrl = attachmentUrl;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SupportTicket getTicket() { return ticket; }
    public void setTicket(SupportTicket ticket) { this.ticket = ticket; }
    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
