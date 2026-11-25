package com.novapetzlife.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RazorpaySignatureVerifier: Empty-Secret Hardening and HMAC Verification Tests")
class RazorpaySignatureVerifierTest {

    private RazorpaySignatureVerifier verifier;
    private static final String VALID_SECRET = "Test_Webhook_Secret_2026_Secure_Key!";
    private static final String SAMPLE_PAYLOAD = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\",\"order_id\":\"order_456\"}}}}";

    @BeforeEach
    void setUp() {
        verifier = new RazorpaySignatureVerifier();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n", " \t \r\n "})
    @DisplayName("Webhook verification must reject null, empty or blank webhook secrets immediately without calculating HMAC")
    void testVerifyWebhookSignature_NullOrBlankSecret_ReturnsFalse(String invalidSecret) {
        String dummySignature = "any_signature_header_val";
        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, dummySignature, invalidSecret);
        assertFalse(result, "Verification must return false when webhook secret is null, empty or blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("Webhook verification must reject null, empty or blank signature headers")
    void testVerifyWebhookSignature_NullOrBlankSignatureHeader_ReturnsFalse(String invalidSignature) {
        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, invalidSignature, VALID_SECRET);
        assertFalse(result, "Verification must return false when signature header is null, empty or blank");
    }

    @Test
    @DisplayName("Webhook verification must reject null request body")
    void testVerifyWebhookSignature_NullPayload_ReturnsFalse() {
        boolean result = verifier.verifyWebhookSignature(null, "some_sig", VALID_SECRET);
        assertFalse(result, "Verification must return false when request body is null");
    }

    @Test
    @DisplayName("Webhook verification succeeds when valid signature is provided with properly configured secret")
    void testVerifyWebhookSignature_ValidSignatureAndSecret_ReturnsTrue() throws Exception {
        String validSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, VALID_SECRET);
        assertNotNull(validSignature);
        assertFalse(validSignature.isBlank());

        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, validSignature, VALID_SECRET);
        assertTrue(result, "Verification must succeed when valid HMAC signature is matched against configured secret");
    }

    @Test
    @DisplayName("Webhook verification tolerates leading and trailing whitespace in signature header")
    void testVerifyWebhookSignature_ValidSignatureWithPadding_ReturnsTrue() throws Exception {
        String validSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, VALID_SECRET);
        String paddedSignature = "   " + validSignature + " \n";

        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, paddedSignature, VALID_SECRET);
        assertTrue(result, "Verification must succeed after trimming whitespace in signature header");
    }

    @Test
    @DisplayName("Webhook verification fails when payload is tampered")
    void testVerifyWebhookSignature_TamperedPayload_ReturnsFalse() throws Exception {
        String validSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, VALID_SECRET);
        String tamperedPayload = SAMPLE_PAYLOAD.replace("pay_123", "pay_attacker_999");

        boolean result = verifier.verifyWebhookSignature(tamperedPayload, validSignature, VALID_SECRET);
        assertFalse(result, "Verification must fail when payload content differs from signed payload");
    }

    @Test
    @DisplayName("Webhook verification fails when signature is wrong")
    void testVerifyWebhookSignature_IncorrectSignature_ReturnsFalse() {
        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, "wrong_signature_hex_value", VALID_SECRET);
        assertFalse(result, "Verification must fail when signature does not match expected HMAC");
    }

    @Test
    @DisplayName("Webhook verification fails when signature was generated using a different secret")
    void testVerifyWebhookSignature_MismatchedSecret_ReturnsFalse() throws Exception {
        String signatureFromDifferentSecret = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, "Different_Secret_999");
        boolean result = verifier.verifyWebhookSignature(SAMPLE_PAYLOAD, signatureFromDifferentSecret, VALID_SECRET);
        assertFalse(result, "Verification must fail when signature was computed using a mismatched secret");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\r\n"})
    @DisplayName("calculateHmacSha256 must throw IllegalArgumentException and never calculate HMAC when secret is null or blank")
    void testCalculateHmacSha256_NullOrBlankSecret_ThrowsIllegalArgumentException(String invalidSecret) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                verifier.calculateHmacSha256(SAMPLE_PAYLOAD, invalidSecret)
        );
        assertTrue(ex.getMessage().contains("must not be null, empty or blank"));
    }

    @Test
    @DisplayName("calculateHmacSha256 must throw IllegalArgumentException when data payload is null")
    void testCalculateHmacSha256_NullData_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                verifier.calculateHmacSha256(null, VALID_SECRET)
        );
        assertTrue(ex.getMessage().contains("must not be null, empty or blank"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("Checkout verification must reject null, empty or blank secret")
    void testVerifyCheckoutSignature_NullOrBlankSecret_ReturnsFalse(String invalidSecret) {
        boolean result = verifier.verifyCheckoutSignature("order_123", "pay_456", "some_sig", invalidSecret);
        assertFalse(result, "Checkout verification must return false when secret is null, empty or blank");
    }

    @Test
    @DisplayName("Checkout verification must reject simulated sandbox signature (no bypass allowed)")
    void testVerifyCheckoutSignature_SimulatedSignature_Rejected() {
        boolean result = verifier.verifyCheckoutSignature("order_123", "pay_456", "simulated_rzp_signature", "test_secret_key");
        assertFalse(result, "Checkout verification must reject simulated_rzp_signature with zero bypass");
    }

    @Test
    @DisplayName("Checkout verification succeeds with authentic HMAC-SHA256 signature")
    void testVerifyCheckoutSignature_ValidHmac_Success() throws Exception {
        String payload = "order_123|pay_456";
        String expectedSig = verifier.calculateHmacSha256(payload, VALID_SECRET);
        boolean result = verifier.verifyCheckoutSignature("order_123", "pay_456", expectedSig, VALID_SECRET);
        assertTrue(result, "Checkout verification must succeed with authentic HMAC-SHA256 signature");
    }
}
