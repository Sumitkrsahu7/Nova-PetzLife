package com.novapetzlife.support.dto;

import com.novapetzlife.support.SupportMessage;
import java.time.Instant;

public class SupportMessageDto {

    private Long id;
    private String senderType;
    private String senderId;
    private String message;
    private String attachmentUrl;
    private Instant createdAt;

    public SupportMessageDto() {}

    public SupportMessageDto(Long id, String senderType, String senderId, String message, String attachmentUrl, Instant createdAt) {
        this.id = id;
        this.senderType = senderType;
        this.senderId = senderId;
        this.message = message;
        this.attachmentUrl = attachmentUrl;
        this.createdAt = createdAt;
    }

    public static SupportMessageDto fromEntity(SupportMessage m) {
        return new SupportMessageDto(
                m.getId(),
                m.getSenderType(),
                m.getSenderId(),
                m.getMessage(),
                m.getAttachmentUrl(),
                m.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
