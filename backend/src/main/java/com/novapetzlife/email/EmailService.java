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
}
