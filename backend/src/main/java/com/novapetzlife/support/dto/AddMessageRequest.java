package com.novapetzlife.support.dto;

import jakarta.validation.constraints.NotBlank;

public class AddMessageRequest {

    @NotBlank(message = "Message text is required")
    private String message;

    private String senderType = "CUSTOMER"; // CUSTOMER, STAFF
    private String senderId;
    private String attachmentUrl;

    public AddMessageRequest() {}

    public AddMessageRequest(String message, String senderType, String senderId, String attachmentUrl) {
        this.message = message;
        this.senderType = senderType != null ? senderType : "CUSTOMER";
        this.senderId = senderId;
        this.attachmentUrl = attachmentUrl;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
}
