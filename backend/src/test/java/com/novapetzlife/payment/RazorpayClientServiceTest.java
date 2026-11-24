package com.novapetzlife.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RazorpayClientServiceTest {

    private RazorpayConfig razorpayConfig;
    private ObjectMapper objectMapper;
    private MockRestServiceServer mockServer;
    private RazorpayClientService razorpayClientService;

    @BeforeEach
    void setUp() {
        razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("rzp_test_mock123");
        razorpayConfig.setKeySecret("mock_secret_xyz");
        razorpayConfig.setCurrency("INR");

        objectMapper = new ObjectMapper();

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.razorpay.com/v1");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        razorpayClientService = new RazorpayClientService(razorpayConfig, objectMapper, restClient);
    }

    @Test
    @DisplayName("Should create Razorpay order successfully with genuine gateway order ID")
    void testCreateOrder_Success() {
        String jsonResponse = """
                {
                    "id": "order_GtwY981249Jk",
                    "entity": "order",
                    "amount": 118000,
                    "amount_paid": 0,
                    "amount_due": 118000,
                    "currency": "INR",
                    "receipt": "ORD-20261124-001",
                    "status": "created",
                    "attempts": 0
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RazorpayClientService.GatewayOrderResult result = razorpayClientService.createOrder(
                "ORD-20261124-001",
                118000L,
                "INR",
                Map.of("customerName", "Aditya Menon")
        );

        mockServer.verify();
        assertNotNull(result);
        assertEquals("order_GtwY981249Jk", result.gatewayOrderId());
        assertEquals(118000L, result.amountInPaise());
        assertEquals("INR", result.currency());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when gateway credentials are unconfigured")
    void testCreateOrder_Unconfigured_ThrowsIllegalStateException() {
        razorpayConfig.setKeyId(null);
        razorpayConfig.setKeySecret(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                razorpayClientService.createOrder("ORD-001", 50000L, "INR", null)
        );
        assertTrue(ex.getMessage().contains("Payment gateway is currently unavailable"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when amount is non-positive")
    void testCreateOrder_NonPositiveAmount_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                razorpayClientService.createOrder("ORD-001", 0L, "INR", null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                razorpayClientService.createOrder("ORD-001", -100L, "INR", null)
        );
    }

    @Test
    @DisplayName("Should handle Razorpay gateway 400 error safely without leaking credentials")
    void testCreateOrder_GatewayRejection_HandlesSafely() {
        String errorJson = """
                {
                    "error": {
                        "code": "BAD_REQUEST_ERROR",
                        "description": "amount must be at least 100 paise"
                    }
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body(errorJson).contentType(MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                razorpayClientService.createOrder("ORD-001", 50L, "INR", null)
        );
        assertTrue(ex.getMessage().contains("rejected order creation"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should create refund successfully via genuine Razorpay payment refund endpoint")
    void testCreateRefund_Success() {
        String jsonResponse = """
                {
                    "id": "rfnd_GtwRef981234",
                    "entity": "refund",
                    "amount": 50000,
                    "currency": "INR",
                    "payment_id": "pay_testPayment123",
                    "status": "processed"
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_testPayment123/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RazorpayClientService.GatewayRefundResult result = razorpayClientService.createRefund(
                "pay_testPayment123",
                50000L,
                "Customer cancellation refund"
        );

        mockServer.verify();
        assertNotNull(result);
        assertEquals("rfnd_GtwRef981234", result.gatewayRefundId());
        assertEquals("processed", result.status());
        assertEquals(50000L, result.amountInPaise());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when paymentId is blank for refund")
    void testCreateRefund_BlankPaymentId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                razorpayClientService.createRefund("", 50000L, "Reason")
        );
        assertThrows(IllegalArgumentException.class, () ->
                razorpayClientService.createRefund(null, 50000L, "Reason")
        );
    }

    @Test
    @DisplayName("Should correctly evaluate isConfigured()")
    void testIsConfigured() {
        assertTrue(razorpayClientService.isConfigured());

        razorpayConfig.setKeyId("");
        assertFalse(razorpayClientService.isConfigured());

        razorpayConfig.setKeyId("rzp_test_123");
        razorpayConfig.setKeySecret(" ");
        assertFalse(razorpayClientService.isConfigured());
    }
}
