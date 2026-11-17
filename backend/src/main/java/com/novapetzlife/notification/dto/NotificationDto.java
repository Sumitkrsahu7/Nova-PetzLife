package com.novapetzlife.notification.dto;

import com.novapetzlife.notification.Notification;
import java.time.Instant;

public class NotificationDto {

    private Long id;
    private Long customerId;
    private String recipient;
    private String channel;
    private String template;
    private String subject;
    private String content;
    private String status;
    private Instant sentAt;
    private Instant createdAt;

    public NotificationDto() {}

    public NotificationDto(Long id, Long customerId, String recipient, String channel, String template, String subject, String content, String status, Instant sentAt, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.content = content;
        this.status = status;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
    }

    public static NotificationDto fromEntity(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getCustomer() != null ? n.getCustomer().getId() : null,
                n.getRecipient(),
                n.getChannel(),
                n.getTemplate(),
                n.getSubject(),
                n.getContent(),
                n.getStatus(),
                n.getSentAt(),
                n.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
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
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
