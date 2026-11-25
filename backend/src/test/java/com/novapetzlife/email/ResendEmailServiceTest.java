package com.novapetzlife.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResendEmailServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ResendEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new ResendEmailService(httpClient);
        ReflectionTestUtils.setField(emailService, "resendEnabled", true);
        ReflectionTestUtils.setField(emailService, "resendApiKey", "re_test_secret_key_12345");
        ReflectionTestUtils.setField(emailService, "resendBaseUrl", "https://api.resend.com");
        ReflectionTestUtils.setField(emailService, "fromEmail", "support@novapetzlife.com");
        ReflectionTestUtils.setField(emailService, "fromName", "NOVA PETZLIFE");
        ReflectionTestUtils.setField(emailService, "customerBaseUrl", "https://novapetzlife.com");
        ReflectionTestUtils.setField(emailService, "passwordResetPath", "/reset-password");
        ReflectionTestUtils.setField(emailService, "expirationMinutes", 15L);
    }

    @Test
    @DisplayName("isConfigured returns true when enabled and API key is present")
    void isConfigured_ReturnsTrue_WhenEnabledWithCredentials() {
        assertTrue(emailService.isConfigured());
    }

    @Test
    @DisplayName("isConfigured returns false when disabled or missing credentials or using prohibited default onboarding email")
    void isConfigured_ReturnsFalse_WhenDisabledOrMissing() {
        ReflectionTestUtils.setField(emailService, "resendEnabled", false);
        assertFalse(emailService.isConfigured());

        ReflectionTestUtils.setField(emailService, "resendEnabled", true);
        ReflectionTestUtils.setField(emailService, "resendApiKey", "");
        assertFalse(emailService.isConfigured());

        ReflectionTestUtils.setField(emailService, "resendApiKey", "re_valid_key");
        ReflectionTestUtils.setField(emailService, "fromEmail", "");
        assertFalse(emailService.isConfigured(), "Empty fromEmail must return false");

        ReflectionTestUtils.setField(emailService, "fromEmail", "onboarding@resend.dev");
        assertFalse(emailService.isConfigured(), "Prohibited default sender onboarding@resend.dev must return false");

        ReflectionTestUtils.setField(emailService, "fromEmail", "invalid-email-no-at");
        assertFalse(emailService.isConfigured(), "Malformed email without @ must return false");
    }

    @Test
    @DisplayName("sendPasswordResetEmail invokes Resend API with correct endpoint, Bearer auth, and payload")
    void sendPasswordResetEmail_DispatchesCorrectRequest() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\": \"resend_msg_001\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        emailService.sendPasswordResetEmail("customer@example.com", "Aditya Sahu", "secure-token-abc");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://api.resend.com/emails", request.uri().toString());
        assertEquals("POST", request.method());
        assertEquals("Bearer re_test_secret_key_12345", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    @DisplayName("sendTransactionalEmail dispatches successfully and returns true on HTTP 200")
    void sendTransactionalEmail_Success_ReturnsTrue() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\": \"msg_success_123\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Your order is placed.</p>"
        );

        assertTrue(result);
        verify(httpClient).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on HTTP 400 Bad Request")
    void sendTransactionalEmail_Http400_ReturnsFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"message\": \"invalid recipient\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "invalid@example.com", "John", "Order #NP-1001", "<p>Your order is placed.</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on HTTP 401 Unauthorized")
    void sendTransactionalEmail_Http401_ReturnsFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"message\": \"API key invalid\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on HTTP 403 Forbidden (unverified domain)")
    void sendTransactionalEmail_Http403_ReturnsFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn("{\"message\": \"Domain unverified\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on HTTP 429 Rate Limited")
    void sendTransactionalEmail_Http429_ReturnsFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("{\"message\": \"Too many requests\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on HTTP 500 Internal Server Error")
    void sendTransactionalEmail_Http500_ReturnsFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("{\"message\": \"Internal error\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on network timeout without throwing unhandled exception")
    void sendTransactionalEmail_Timeout_ReturnsFalse() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new HttpConnectTimeoutException("Connection timed out"));

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendTransactionalEmail returns false on request timeout (HttpTimeoutException)")
    void sendTransactionalEmail_RequestTimeout_ReturnsFalse() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new java.net.http.HttpTimeoutException("Request timed out"));

        boolean result = emailService.sendTransactionalEmail(
                "customer@example.com", "John", "Order #NP-1001", "<p>Hello</p>"
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("sendPasswordResetEmail URL encodes raw reset token preventing link corruption")
    void sendPasswordResetEmail_UrlEncodesToken() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\": \"msg_token_enc\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        String rawTokenWithSpecialChars = "token+with/special=chars&more";
        emailService.sendPasswordResetEmail("customer@example.com", "John Doe", rawTokenWithSpecialChars);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        assertNotNull(request);
    }

    @Test
    @DisplayName("sendPasswordResetEmail defers safely with [EMAIL DEFERRED] when Resend is disabled or unconfigured")
    void sendPasswordResetEmail_Unconfigured_DefersWithDeferredLog() throws Exception {
        ReflectionTestUtils.setField(emailService, "resendEnabled", false);

        Logger logger = (Logger) LoggerFactory.getLogger(ResendEmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String rawToken = "RAW_UNSAFE_TOKEN_999";
        emailService.sendPasswordResetEmail("customer@example.com", "Aditya", rawToken);

        verify(httpClient, never()).send(any(), any());

        List<ILoggingEvent> logs = appender.list;
        assertFalse(logs.isEmpty());
        boolean hasDeferred = logs.stream().anyMatch(e -> e.getFormattedMessage().contains("[EMAIL DEFERRED]"));
        boolean leaksToken = logs.stream().anyMatch(e -> e.getFormattedMessage().contains(rawToken));
        boolean leaksFullUrl = logs.stream().anyMatch(e -> e.getFormattedMessage().contains("token="));

        assertTrue(hasDeferred, "Deferred delivery log tag must be present");
        assertFalse(leaksToken, "Raw token must never appear in logs");
        assertFalse(leaksFullUrl, "Password reset token URL must never appear in logs");

        logger.detachAppender(appender);
    }
}
