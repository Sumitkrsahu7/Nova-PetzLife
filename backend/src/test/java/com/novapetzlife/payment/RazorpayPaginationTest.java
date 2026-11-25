package com.novapetzlife.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RazorpayPaginationTest {

    private RazorpayConfig razorpayConfig;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private RazorpayClientService razorpayClientService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("rzp_test_key123");
        razorpayConfig.setKeySecret("rzp_test_secret456");

        restClientBuilder = RestClient.builder().baseUrl("https://api.razorpay.com/v1");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        objectMapper = new ObjectMapper();
        razorpayClientService = new RazorpayClientService(razorpayConfig, objectMapper, restClient);
    }

    @Test
    @DisplayName("Single page pagination: returns all items when fewer than page size (count=50)")
    void testPagination_SinglePage() {
        String json = """
        {
          "entity": "collection",
          "count": 2,
          "items": [
            {"id": "rfnd_001", "amount": 10000, "status": "processed", "receipt": "rcpt_001", "currency": "INR"},
            {"id": "rfnd_002", "amount": 20000, "status": "processed", "receipt": "rcpt_002", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_123/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<RazorpayClientService.GatewayPaymentRefundItem> refunds = razorpayClientService.fetchRefundsForPayment("pay_123");
        mockServer.verify();

        assertEquals(2, refunds.size());
        assertEquals("rfnd_001", refunds.get(0).refundId());
        assertEquals("rcpt_001", refunds.get(0).receipt());
        assertEquals("rfnd_002", refunds.get(1).refundId());
    }

    @Test
    @DisplayName("Multi-page pagination: fetches page 1 and page 2 until terminal page reached")
    void testPagination_MultiPage() {
        // Page 1: returns 50 items (full page)
        StringBuilder sb1 = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb1.append(",");
            sb1.append(String.format("{\"id\":\"rfnd_p1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_p1_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb1.append("]}");

        // Page 2: returns 5 items (< 50, terminal page)
        String json2 = """
        {
          "entity": "collection",
          "count": 2,
          "items": [
            {"id": "rfnd_p2_01", "amount": 5000, "status": "processed", "receipt": "rcpt_target_match", "currency": "INR"},
            {"id": "rfnd_p2_02", "amount": 6000, "status": "processed", "receipt": "rcpt_p2_02", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_multi/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_multi/refunds?count=50&skip=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json2, MediaType.APPLICATION_JSON));

        List<RazorpayClientService.GatewayPaymentRefundItem> refunds = razorpayClientService.fetchRefundsForPayment("pay_multi");
        mockServer.verify();

        assertEquals(52, refunds.size());
        assertEquals("rfnd_p1_0", refunds.get(0).refundId());
        assertEquals("rfnd_p2_01", refunds.get(50).refundId());
        assertEquals("rcpt_target_match", refunds.get(50).receipt());
    }

    @Test
    @DisplayName("Pagination error: gateway HTTP 500 on later page throws GatewayReconciliationException")
    void testPagination_ErrorOnLaterPage_ThrowsException() {
        StringBuilder sb1 = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb1.append(",");
            sb1.append(String.format("{\"id\":\"rfnd_p1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_p1_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb1.append("]}");

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_err/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_err/refunds?count=50&skip=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_err")
        );
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty refund list: returns empty list cleanly")
    void testPagination_EmptyList() {
        String json = "{\"entity\":\"collection\",\"count\":0,\"items\":[]}";

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_empty/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<RazorpayClientService.GatewayPaymentRefundItem> refunds = razorpayClientService.fetchRefundsForPayment("pay_empty");
        mockServer.verify();

        assertTrue(refunds.isEmpty());
    }

    @Test
    @DisplayName("Cycle detection: repeated identical page throws GatewayReconciliationException")
    void testPagination_CycleDetection_ThrowsException() {
        // Page 1: 50 items
        StringBuilder sb1 = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb1.append(",");
            sb1.append(String.format("{\"id\":\"rfnd_dup_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb1.append("]}");

        // Page 2: returns the exact same 50 items (server loop or cursor glitch)
        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_cycle/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_cycle/refunds?count=50&skip=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_cycle")
        );
        assertTrue(ex.getMessage().contains("Non-progressing pagination loop detected"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Safety limit: exceeding MAX_SAFETY_RECORDS (1000) throws GatewayReconciliationException")
    void testPagination_SafetyLimitExceeded_ThrowsException() {
        // Mock 20 full pages of 50 items = 1000 items
        for (int p = 0; p < 20; p++) {
            int currentSkip = p * 50;
            StringBuilder sb = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
            for (int i = 0; i < 50; i++) {
                if (i > 0) sb.append(",");
                int id = currentSkip + i;
                sb.append(String.format("{\"id\":\"rfnd_saf_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_%d\",\"currency\":\"INR\"}", id, id));
            }
            sb.append("]}");

            mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_limit/refunds?count=50&skip=" + currentSkip))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(sb.toString(), MediaType.APPLICATION_JSON));
        }

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_limit")
        );
        assertTrue(ex.getMessage().contains("operational safety limit of 1000 records"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Partial duplicate page: only new items are appended and duplicate records are omitted")
    void testPagination_MixedDuplicatesAndNewItems_OnlyAppendsNew() {
        // Page 1: 50 items (rfnd_p1_0 to rfnd_p1_49)
        StringBuilder sb1 = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb1.append(",");
            sb1.append(String.format("{\"id\":\"rfnd_p1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_p1_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb1.append("]}");

        // Page 2: 20 items: 5 duplicates from Page 1 (rfnd_p1_45..49) and 15 new items (rfnd_p2_0..14)
        StringBuilder sb2 = new StringBuilder("{\"entity\":\"collection\",\"count\":20,\"items\":[");
        for (int i = 45; i < 50; i++) {
            if (i > 45) sb2.append(",");
            sb2.append(String.format("{\"id\":\"rfnd_p1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_p1_%d\",\"currency\":\"INR\"}", i, i));
        }
        for (int i = 0; i < 15; i++) {
            sb2.append(",");
            sb2.append(String.format("{\"id\":\"rfnd_p2_%d\",\"amount\":2000,\"status\":\"processed\",\"receipt\":\"rcpt_p2_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb2.append("]}");

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mixed/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mixed/refunds?count=50&skip=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb2.toString(), MediaType.APPLICATION_JSON));

        List<RazorpayClientService.GatewayPaymentRefundItem> refunds = razorpayClientService.fetchRefundsForPayment("pay_mixed");
        mockServer.verify();

        // 50 from page 1 + 15 new from page 2 = 65 total items (the 5 duplicates are not appended again)
        assertEquals(65, refunds.size(), "Total items must be exactly 65 without duplicates");

        // Verify distinctness of all IDs
        java.util.Set<String> distinctIds = new java.util.HashSet<>();
        for (var item : refunds) {
            assertTrue(distinctIds.add(item.refundId()), "Found duplicate refund ID in collected results: " + item.refundId());
        }
        assertEquals(65, distinctIds.size());
    }

    @Test
    @DisplayName("Page containing only duplicate items aborts as non-progressing cycle")
    void testPagination_OnlyDuplicatesOnLaterPage_AbortsAsCycle() {
        // Page 1: 50 items
        StringBuilder sb1 = new StringBuilder("{\"entity\":\"collection\",\"count\":50,\"items\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb1.append(",");
            sb1.append(String.format("{\"id\":\"rfnd_dup1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_d1_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb1.append("]}");

        // Page 2: returns 10 items, but all 10 items are duplicates from Page 1
        StringBuilder sb2 = new StringBuilder("{\"entity\":\"collection\",\"count\":10,\"items\":[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb2.append(",");
            sb2.append(String.format("{\"id\":\"rfnd_dup1_%d\",\"amount\":1000,\"status\":\"processed\",\"receipt\":\"rcpt_d1_%d\",\"currency\":\"INR\"}", i, i));
        }
        sb2.append("]}");

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_dup_cycle/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb1.toString(), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_dup_cycle/refunds?count=50&skip=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sb2.toString(), MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_dup_cycle")
        );
        assertTrue(ex.getMessage().contains("Non-progressing pagination loop detected"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Fail-closed: malformed gateway refund item with missing ID throws GatewayReconciliationException")
    void testPagination_MalformedRecord_MissingId_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 1,
          "items": [
            {"amount": 1000, "status": "processed", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mal_id/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_mal_id")
        );
        assertTrue(ex.getMessage().contains("missing or blank refund ID"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Fail-closed: malformed gateway refund item with non-positive amount throws GatewayReconciliationException")
    void testPagination_MalformedRecord_InvalidAmount_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 1,
          "items": [
            {"id": "rfnd_mal_amt", "amount": 0, "status": "processed", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mal_amt/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_mal_amt")
        );
        assertTrue(ex.getMessage().contains("invalid amount"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Fail-closed: malformed gateway refund item with missing status throws GatewayReconciliationException")
    void testPagination_MalformedRecord_MissingStatus_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 1,
          "items": [
            {"id": "rfnd_mal_stat", "amount": 1000, "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mal_stat/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_mal_stat")
        );
        assertTrue(ex.getMessage().contains("missing status"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Fail-closed: mixed page containing valid and malformed item throws GatewayReconciliationException immediately")
    void testPagination_MixedPage_ValidAndMalformedItem_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 2,
          "items": [
            {"id": "rfnd_valid_01", "amount": 1000, "status": "processed", "currency": "INR"},
            {"id": "   ", "amount": 2000, "status": "processed", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_mixed_mal/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_mixed_mal")
        );
        assertTrue(ex.getMessage().contains("missing or blank refund ID"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Exact integer amount: accepts standard integer amounts in paise")
    void testAmountParsing_ExactInteger() {
        JsonNode node = objectMapper.valueToTree(50000L);
        long parsed = RazorpayClientService.parseGatewayAmountInPaise(node, "rfnd_test", "pay_test");
        assertEquals(50000L, parsed);
    }

    @Test
    @DisplayName("Integral decimal representation: accepts mathematically integral decimals (e.g. 1000.0, 1000.00)")
    void testAmountParsing_IntegralDecimal() {
        JsonNode node1 = objectMapper.valueToTree(new java.math.BigDecimal("1000.0"));
        long parsed1 = RazorpayClientService.parseGatewayAmountInPaise(node1, "rfnd_test", "pay_test");
        assertEquals(1000L, parsed1);

        JsonNode node2 = objectMapper.valueToTree(new java.math.BigDecimal("50000.00"));
        long parsed2 = RazorpayClientService.parseGatewayAmountInPaise(node2, "rfnd_test", "pay_test");
        assertEquals(50000L, parsed2);
    }

    @Test
    @DisplayName("Fractional amount: rejects 100.5 and 100.9 without silent truncation")
    void testAmountParsing_FractionalAmounts_Rejected() {
        JsonNode node100_5 = objectMapper.valueToTree(100.5);
        var ex1 = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(node100_5, "rfnd_test", "pay_test")
        );
        assertTrue(ex1.getMessage().contains("fractional amount"));

        JsonNode node100_9 = objectMapper.valueToTree(100.9);
        var ex2 = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(node100_9, "rfnd_test", "pay_test")
        );
        assertTrue(ex2.getMessage().contains("fractional amount"));

        JsonNode nodeFracDecimal = objectMapper.valueToTree(new java.math.BigDecimal("100.001"));
        var ex3 = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(nodeFracDecimal, "rfnd_test", "pay_test")
        );
        assertTrue(ex3.getMessage().contains("fractional amount"));
    }

    @Test
    @DisplayName("Zero and negative amounts: rejects 0, 0.0, -100, -100.5")
    void testAmountParsing_ZeroAndNegative_Rejected() {
        JsonNode zeroInt = objectMapper.valueToTree(0);
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(zeroInt, "rfnd_test", "pay_test")
        );

        JsonNode zeroDec = objectMapper.valueToTree(new java.math.BigDecimal("0.00"));
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(zeroDec, "rfnd_test", "pay_test")
        );

        JsonNode negInt = objectMapper.valueToTree(-100);
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(negInt, "rfnd_test", "pay_test")
        );

        JsonNode negDec = objectMapper.valueToTree(new java.math.BigDecimal("-100.50"));
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(negDec, "rfnd_test", "pay_test")
        );
    }

    @Test
    @DisplayName("Missing, null, string, and boolean values: rejected fail-closed")
    void testAmountParsing_MissingNullStringBoolean_Rejected() {
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(null, "rfnd_test", "pay_test")
        );

        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(com.fasterxml.jackson.databind.node.NullNode.getInstance(), "rfnd_test", "pay_test")
        );

        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(com.fasterxml.jackson.databind.node.MissingNode.getInstance(), "rfnd_test", "pay_test")
        );

        JsonNode stringNode = objectMapper.valueToTree("1000");
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(stringNode, "rfnd_test", "pay_test")
        );

        JsonNode boolNode = objectMapper.valueToTree(true);
        assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(boolNode, "rfnd_test", "pay_test")
        );
    }

    @Test
    @DisplayName("Long overflow: rejects amount exceeding 64-bit signed long")
    void testAmountParsing_LongOverflow_Rejected() {
        JsonNode hugeNode = objectMapper.valueToTree(new java.math.BigDecimal("10000000000000000000000000"));
        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                RazorpayClientService.parseGatewayAmountInPaise(hugeNode, "rfnd_test", "pay_test")
        );
        assertTrue(ex.getMessage().contains("outside 64-bit long range"));
    }

    @Test
    @DisplayName("Mixed valid and malformed page via mockServer: fractional 100.5 item aborts reconciliation immediately")
    void testPagination_MixedPage_FractionalAmount_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 2,
          "items": [
            {"id": "rfnd_valid_01", "amount": 1000, "status": "processed", "currency": "INR"},
            {"id": "rfnd_frac_02", "amount": 100.5, "status": "processed", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_frac/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_frac")
        );
        assertTrue(ex.getMessage().contains("fractional amount"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Mixed valid and malformed page via mockServer: fractional 100.9 item aborts reconciliation immediately")
    void testPagination_MixedPage_Fractional_100_9_ThrowsException() {
        String json = """
        {
          "entity": "collection",
          "count": 2,
          "items": [
            {"id": "rfnd_valid_01", "amount": 1000, "status": "processed", "currency": "INR"},
            {"id": "rfnd_frac_09", "amount": 100.9, "status": "processed", "currency": "INR"}
          ]
        }
        """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_frac9/refunds?count=50&skip=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var ex = assertThrows(RazorpayClientService.GatewayReconciliationException.class, () ->
                razorpayClientService.fetchRefundsForPayment("pay_frac9")
        );
        assertTrue(ex.getMessage().contains("fractional amount"));
        mockServer.verify();
    }
}
