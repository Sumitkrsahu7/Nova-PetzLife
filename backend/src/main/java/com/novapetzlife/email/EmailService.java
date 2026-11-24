package com.novapetzlife.email;

public interface EmailService {

    /**
     * Send password reset email containing secure link with reset token.
     *
     * @param toEmail Recipient email address
     * @param customerName Recipient customer name
     * @param resetToken Raw unhashed reset token
     */
    void sendPasswordResetEmail(String toEmail, String customerName, String resetToken);

    /**
     * Send transactional email (order confirmation, invoice, status updates).
     *
     * @param toEmail Recipient email address
     * @param recipientName Recipient name
     * @param subject Email subject
     * @param textContent Plain text or HTML content
     * @return true if accepted by provider, false if deferred/failed
     */
    boolean sendTransactionalEmail(String toEmail, String recipientName, String subject, String textContent);
}
