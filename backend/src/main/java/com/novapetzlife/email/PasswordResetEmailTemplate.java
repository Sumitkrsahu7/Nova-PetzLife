package com.novapetzlife.email;

import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Shared password reset email content builder for NOVA PETZLIFE.
 * Produces an HTML-escaped, responsive email body and matching plain-text fallback.
 */
public final class PasswordResetEmailTemplate {

    public static final String SUBJECT = "Reset Your NOVA PETZLIFE Account Password";

    private PasswordResetEmailTemplate() {
    }

    public static String buildHtml(String customerName, String resetLink, long expirationMinutes) {
        String displayName = StringUtils.hasText(customerName) ? customerName.trim() : "Valued Customer";
        String safeName = HtmlUtils.htmlEscape(displayName);
        String safeLink = HtmlUtils.htmlEscape(resetLink != null ? resetLink : "");

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Reset Your Password — NOVA PETZLIFE</title>
            </head>
            <body style="margin:0;padding:0;background-color:#F8FAFC;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#0F172A;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:580px;background:#FFFFFF;border-radius:12px;overflow:hidden;border:1px solid #E2E8F0;">
                      <tr>
                        <td style="background:#155C3B;padding:24px;text-align:center;">
                          <h1 style="margin:0;color:#FFFFFF;font-size:22px;font-weight:800;letter-spacing:0.5px;">
                            &#128062; NOVA PETZLIFE
                          </h1>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:32px 28px;">
                          <h2 style="margin:0 0 16px;color:#1E293B;font-size:18px;">Hello %s,</h2>
                          <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#475569;">
                            We received a request to reset the password for your <strong>NOVA PETZLIFE</strong> customer account.
                          </p>
                          <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#475569;">
                            Click the button below to choose a new password:
                          </p>
                          <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 auto 24px;">
                            <tr>
                              <td align="center" style="border-radius:8px;background-color:#248B58;">
                                <a href="%s" target="_blank" rel="noopener noreferrer"
                                   style="display:inline-block;padding:13px 30px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:8px;">
                                  Reset My Password
                                </a>
                              </td>
                            </tr>
                          </table>
                          <div style="background:#F0FDF4;border-left:4px solid #248B58;padding:14px 18px;border-radius:4px;font-size:13.5px;color:#166534;margin:0 0 24px;">
                            <strong>Security Notice:</strong> This password reset link expires in <strong>%d minutes</strong> and can only be used once.
                          </div>
                          <p style="margin:0 0 8px;font-size:13.5px;line-height:1.5;color:#475569;">
                            If the button above doesn't work, copy and paste this link into your browser:
                          </p>
                          <p style="margin:0 0 24px;word-break:break-all;font-size:13px;color:#248B58;">
                            %s
                          </p>
                          <div style="border-top:1px solid #E2E8F0;padding-top:18px;font-size:13px;line-height:1.5;color:#64748B;">
                            <strong>Didn't request this?</strong> You can safely ignore this email. Your account remains secure and your password will not change unless you click the link above.
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td style="background:#F8FAFC;padding:20px;text-align:center;font-size:12px;line-height:1.5;color:#94A3B8;border-top:1px solid #E2E8F0;">
                          <p style="margin:0 0 4px;">
                            &copy; NOVA PETZLIFE &bull; Kuravankonam Junction, Thiruvananthapuram, Kerala 695003
                          </p>
                          <p style="margin:0;">
                            This is an automated security notification. Please do not reply directly to this email.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(safeName, safeLink, expirationMinutes, safeLink);
    }

    public static String buildPlainText(String customerName, String resetLink, long expirationMinutes) {
        String displayName = StringUtils.hasText(customerName) ? customerName.trim() : "Valued Customer";
        String safeLink = resetLink != null ? resetLink : "";

        return """
            Hello %s,

            We received a request to reset the password for your NOVA PETZLIFE customer account.

            Click or paste the link below into your browser to set a new password:
            %s

            For your security, this link will expire in %d minutes and can only be used once.

            If you did not request a password reset, you can safely ignore this email—your current password will remain unchanged.

            Need help? Contact us or visit our store:
            NOVA PETZLIFE
            Novapetz, Kuravankonam Junction, Kuravankonam Ambalamukku Rd,
            Thiruvananthapuram, Kerala 695003

            This is an automated security email. Please do not reply directly to this message.
            """.formatted(displayName, safeLink, expirationMinutes);
    }
}
