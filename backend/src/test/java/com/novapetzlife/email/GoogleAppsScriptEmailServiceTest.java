package com.novapetzlife.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleAppsScriptEmailServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_SECRET = "super_secret_bridge_token_value_123";
    private static final String TEST_WEBHOOK = "https://script.google.com/macros/s/AKfycbTestDeployment/exec";

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GoogleAppsScriptEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new GoogleAppsScriptEmailService(httpClient);
        ReflectionTestUtils.setField(emailService, "enabled", true);
        ReflectionTestUtils.setField(emailService, "webhookUrl", TEST_WEBHOOK);
        ReflectionTestUtils.setField(emailService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(emailService, "fromName", "NOVA PETZLIFE");
        ReflectionTestUtils.setField(emailService, "customerBaseUrl", "https://novapetzlife.com");
        ReflectionTestUtils.setField(emailService, "passwordResetPath", "/reset-password");
        ReflectionTestUtils.setField(emailService, "expirationMinutes", 15L);
    }

    @Test
    @DisplayName("isConfigured validates enabled flag, HTTPS webhook URL, and non-blank secret")
    void isConfigured_ValidatesAllRequiredFields() {
        assertTrue(emailService.isConfigured());

        ReflectionTestUtils.setField(emailService, "enabled", false);
        assertFalse(emailService.isConfigured());

        ReflectionTestUtils.setField(emailService, "enabled", true);
        ReflectionTestUtils.setField(emailService, "webhookUrl", "http://script.google.com/macros/s/insecure/exec");
        assertFalse(emailService.isConfigured(), "Non-HTTPS webhook URL must be rejected");

        ReflectionTestUtils.setField(emailService, "webhookUrl", "");
        assertFalse(emailService.isConfigured());

        ReflectionTestUtils.setField(emailService, "webhookUrl", TEST_WEBHOOK);
        ReflectionTestUtils.setField(emailService, "secret", "  ");
        assertFalse(emailService.isConfigured());
    }

    @Test
    @DisplayName("sendPasswordResetEmail sends JSON payload with escaped HTML, URL-encoded token, and secret")
    void sendPasswordResetEmail_DispatchesExpectedPayload() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"ok\": true, \"message\": \"Password reset email dispatched\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        String rawToken = "token+with/special=chars";
        emailService.sendPasswordResetEmail("customer@gmail.com", "<script>Aditya</script>", rawToken);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        assertEquals(TEST_WEBHOOK, request.uri().toString());
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));

        String body = readBody(request);
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertEquals(TEST_SECRET, json.get("secret").asText());
        assertEquals("customer@gmail.com", json.get("to").asText());
        assertEquals(PasswordResetEmailTemplate.SUBJECT, json.get("subject").asText());
        assertEquals("NOVA PETZLIFE", json.get("fromName").asText());

        String html = json.get("html").asText();
        assertTrue(html.contains("https://novapetzlife.com/reset-password?token=token%2Bwith%2Fspecial%3Dchars"));
        assertTrue(html.contains("&lt;script&gt;Aditya&lt;/script&gt;"));
        assertFalse(html.contains("<script>Aditya</script>"));
        assertTrue(json.get("text").asText().contains("https://novapetzlife.com/reset-password?token=token%2Bwith%2Fspecial%3Dchars"));
    }

    @Test
    @DisplayName("sendPasswordResetEmail defers safely without leaking secrets, tokens, or full email when unconfigured")
    void sendPasswordResetEmail_Unconfigured_DefersWithoutLeakingSecrets() throws Exception {
        ReflectionTestUtils.setField(emailService, "webhookUrl", "");

        Logger logger = (Logger) LoggerFactory.getLogger(GoogleAppsScriptEmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String rawToken = "RAW_SECRET_RESET_TOKEN_999";
        try {
            emailService.sendPasswordResetEmail("kavita.customer@gmail.com", "Kavita", rawToken);

            verify(httpClient, never()).send(any(), any());

            List<ILoggingEvent> logs = appender.list;
            assertFalse(logs.isEmpty());
            assertTrue(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("[EMAIL DEFERRED]")));
            assertTrue(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("ka***@gmail.com")));
            assertFalse(logs.stream().anyMatch(e -> e.getFormattedMessage().contains(rawToken)));
            assertFalse(logs.stream().anyMatch(e -> e.getFormattedMessage().contains(TEST_SECRET)));
            assertFalse(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("token=")));
            assertFalse(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("kavita.customer@gmail.com")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("sendPasswordResetEmail handles rejected webhook JSON, non-JSON HTML, HTTP errors, and timeouts gracefully")
    void sendPasswordResetEmail_HandlesFailuresGracefully() throws Exception {
        // Case 1: HTTP 200 with ok=false
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"ok\": false, \"error\": \"Unauthorized\"}");
        doReturn(httpResponse).when(httpClient).send(any(), any());
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("customer@gmail.com", "John", "tok1"));

        // Case 2: Non-JSON HTML sign-in page
        when(httpResponse.body()).thenReturn("<html><body>Sign in</body></html>");
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("customer@gmail.com", "John", "tok2"));

        // Case 3: Non-2xx status
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Server Error");
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("customer@gmail.com", "John", "tok3"));

        // Case 4: Connect timeout & request timeout
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpConnectTimeoutException("Connect timeout"))
                .thenThrow(new HttpTimeoutException("Read timeout"));
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("customer@gmail.com", "John", "tok4"));
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("customer@gmail.com", "John", "tok5"));
    }

    @Test
    @DisplayName("sendTransactionalEmail is disabled and never dispatches non-password-reset emails")
    void sendTransactionalEmail_IsScopedOutAndReturnsFalse() throws Exception {
        boolean result = emailService.sendTransactionalEmail(
                "customer@gmail.com", "John", "Order #NP-1001", "<p>Order confirmed</p>"
        );

        assertFalse(result);
        verify(httpClient, never()).send(any(), any());
    }

    private static String readBody(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        StringBuilder sb = new StringBuilder();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                sb.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                throw new RuntimeException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return sb.toString();
    }
}
