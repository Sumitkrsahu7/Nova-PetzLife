package com.novapetzlife.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateInquiryRequest {

    @NotBlank(message = "Customer name is required")
    private String name;

    @NotBlank(message = "Email address is required")
    @Email(message = "Valid email address is required")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    private String orderNumber;

    @NotBlank(message = "Inquiry subject is required")
    private String subject;

    @NotBlank(message = "Message details are required")
    private String message;

    private String priority = "MEDIUM";

    public CreateInquiryRequest() {}

    public CreateInquiryRequest(String name, String email, String phone, String orderNumber, String subject, String message, String priority) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.orderNumber = orderNumber;
        this.subject = subject;
        this.message = message;
        this.priority = priority != null ? priority : "MEDIUM";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
