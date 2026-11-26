/**
 * NOVA PETZLIFE — Temporary Password Reset Email Bridge (Google Apps Script)
 *
 * Allows Render Free Tier (which blocks outbound SMTP ports 25, 465, and 587)
 * to send customer password reset emails directly from your Gmail account over HTTPS (Port 443).
 *
 * SETUP INSTRUCTIONS:
 * 1. Go to https://script.google.com and create a New Project (e.g. "NovaPetzLife-ResetMailer").
 * 2. Paste this entire file into Code.gs and save.
 * 3. Open Project Settings (gear icon) -> Script Properties -> Add script property:
 *      Property: BRIDGE_SECRET
 *      Value:    <generate a strong random 32+ character secret>
 * 4. Click Deploy -> New deployment -> Select type: "Web app":
 *      - Execute as: "Me (<your-gmail-address>)"
 *      - Who has access: "Anyone"
 * 5. Authorize the Gmail send permission prompt on first deploy.
 * 6. Copy the generated Web App URL (ends with /exec) and set these backend env vars:
 *      GMAIL_BRIDGE_ENABLED=true
 *      GMAIL_BRIDGE_WEBHOOK_URL=https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec
 *      GMAIL_BRIDGE_SECRET=<same BRIDGE_SECRET value>
 */

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ ok: false, error: 'Empty request payload' });
    }

    var expectedSecret = PropertiesService.getScriptProperties().getProperty('BRIDGE_SECRET');
    if (!expectedSecret) {
      return jsonResponse({ ok: false, error: 'BRIDGE_SECRET script property is not configured' });
    }

    var payload = JSON.parse(e.postData.contents);
    if (!payload.secret || !constantTimeEquals(String(payload.secret), String(expectedSecret))) {
      return jsonResponse({ ok: false, error: 'Unauthorized' });
    }

    var to = payload.to ? String(payload.to).trim() : '';
    var subject = payload.subject ? String(payload.subject) : 'Reset Your NOVA PETZLIFE Account Password';
    var textBody = payload.text ? String(payload.text) : '';
    var htmlBody = payload.html ? String(payload.html) : '';
    var fromName = payload.fromName ? String(payload.fromName).trim() : 'NOVA PETZLIFE';

    if (!to || to.indexOf('@') === -1) {
      return jsonResponse({ ok: false, error: 'Invalid recipient email address' });
    }

    var options = {
      name: fromName
    };
    if (htmlBody) {
      options.htmlBody = htmlBody;
    }

    GmailApp.sendEmail(to, subject, textBody || 'Please open this email in an HTML-capable client.', options);

    return jsonResponse({ ok: true, message: 'Password reset email dispatched' });
  } catch (err) {
    return jsonResponse({ ok: false, error: err && err.message ? err.message : 'Unexpected Apps Script error' });
  }
}

function constantTimeEquals(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  var diff = 0;
  for (var i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
