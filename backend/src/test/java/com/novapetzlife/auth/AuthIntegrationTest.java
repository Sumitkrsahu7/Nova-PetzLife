package com.novapetzlife.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.security.AuthCookieCsrfFilter;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should successfully register a new customer and set refresh cookie without exposing it in JSON")
    void shouldRegisterNewCustomer() throws Exception {
        String email = "customer_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest request = new RegisterRequest(
                email,
                "StrongPassword123!",
                "Anand",
                "Nair",
                "+919876543210"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.firstName").value("Anand"))
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_CUSTOMER")))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);
        assertTrue(refreshCookie.isHttpOnly());
        assertTrue(refreshCookie.getValue().length() > 20);
    }

    @Test
    @DisplayName("Should fail registration with duplicate email")
    void shouldFailDuplicateRegistration() throws Exception {
        String email = "dup_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest request = new RegisterRequest(
                email,
                "Password123!",
                "Test",
                "User",
                "+919876543211"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should authenticate user, set refresh cookie, and exclude refresh token from JSON")
    void shouldLoginSuccessfully() throws Exception {
        String email = "login_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "SecretP@ssword1",
                "Kavitha",
                "Menon",
                "+919876543212"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest(email, "SecretP@ssword1");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);
        assertTrue(refreshCookie.isHttpOnly());
    }

    @Test
    @DisplayName("Should reject unauthorized access to protected admin endpoints with HTTP 401")
    void shouldRejectUnauthorizedAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should rotate refresh token via HttpOnly cookie and return new access token without JSON refresh token")
    void shouldRotateRefreshTokenViaCookie() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie loginCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(loginCookie);

        // Perform Refresh via cookie with CSRF headers
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(loginCookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertNotNull(rotatedCookie);
        assertNotEquals(loginCookie.getValue(), rotatedCookie.getValue());
    }

    @Test
    @DisplayName("Should safely recover concurrent refresh requests within grace period without logging out")
    void shouldRecoverConcurrentRefreshRequests() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/auth/refresh")
                                    .cookie(cookie)
                                    .header("Origin", "http://localhost:4200")
                                    .header("X-Requested-With", "XMLHttpRequest"))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Both concurrent requests recover successfully with HTTP 200 and valid access tokens
        assertEquals(2, successCount.get(), "Both concurrent requests must succeed with valid sessions");
        assertEquals(0, failureCount.get(), "Neither concurrent tab should fail or be logged out");

        // Database assertion: only one active replacement refresh token exists
        User admin = userRepository.findByEmail("test@gmail.com").orElseThrow();
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(admin.getId());
        assertEquals(1, activeTokens.size(), "Only one active replacement token must exist in the database");
    }

    @Test
    @DisplayName("Should persist session revocation in database during token reuse attack after grace period")
    void shouldPersistRevocationOnReuseAttack() throws Exception {
        String email = "reuse_attack_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "StrongPassword123!",
                "Reuse",
                "Tester",
                "+919876543299"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest(email, "StrongPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie originalCookie = loginResult.getResponse().getCookie("refreshToken");
        User user = userRepository.findByEmail(email).orElseThrow();

        // Rotate once: originalCookie is now revoked, and a new active token is created
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalCookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());

        // Ensure there are active refresh tokens for user
        List<RefreshToken> activeBefore = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId());
        assertFalse(activeBefore.isEmpty());

        // Simulate elapsed grace period by setting revokedAt to 30 seconds ago
        String hash = RefreshTokenService.hashToken(originalCookie.getValue());
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        oldToken.setRevokedAt(Instant.now().minusSeconds(30));
        refreshTokenRepository.save(oldToken);

        // Attacker attempts reuse with originalCookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalCookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // Assert database state: all active tokens for this user must remain revoked
        List<RefreshToken> activeAfter = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId());
        assertTrue(activeAfter.isEmpty(), "All active sessions for the user must be revoked in database after reuse attack");
    }

    @Test
    @DisplayName("Should reject refresh when refresh token is expired")
    void shouldRejectExpiredRefreshToken() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");
        String hash = RefreshTokenService.hashToken(cookie.getValue());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject refresh when user account is deactivated")
    void shouldRejectRefreshForDeactivatedUser() throws Exception {
        String email = "deactive_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "StrongPassword123!",
                "Deactive",
                "User",
                "+919876543288"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest(email, "StrongPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // Restore active status
        user.setIsActive(true);
        userRepository.save(user);
    }

    @Test
    @DisplayName("Should revoke refresh token upon logout and clear cookie")
    void shouldRevokeRefreshTokenOnLogout() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie loginCookie = loginResult.getResponse().getCookie("refreshToken");

        // Logout with cookie
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(loginCookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // Verify clean cookie with maxAge=0
        Cookie cleanCookie = logoutResult.getResponse().getCookie("refreshToken");
        assertNotNull(cleanCookie);
        assertEquals(0, cleanCookie.getMaxAge());

        // Database assertion: token must be revoked
        String hash = RefreshTokenService.hashToken(loginCookie.getValue());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        assertNotNull(token.getRevokedAt());

        // Attempting to refresh with logged-out cookie must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(loginCookie)
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should be safe to call logout repeatedly without cookies")
    void shouldBeSafeOnRepeatedLogout() throws Exception {
        // Logout with no cookie must return 200 OK and clean cookie
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andReturn();
        assertNotNull(res1.getResponse().getCookie("refreshToken"));
        assertEquals(0, res1.getResponse().getCookie("refreshToken").getMaxAge());

        // Second call
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andReturn();
        assertNotNull(res2.getResponse().getCookie("refreshToken"));
    }

    @Test
    @DisplayName("Should reject refresh when refresh cookie is missing")
    void shouldRejectMissingRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject refresh when refresh cookie is invalid")
    void shouldRejectInvalidRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "completely_bogus_token_hash_value"))
                        .header("Origin", "http://localhost:4200")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    // --- ISSUE 1 CSRF TEST MATRIX ---

    @Test
    @DisplayName("CSRF Test 1: Should permit refresh request with trusted Origin and X-Requested-With")
    void shouldAllowTrustedOriginCsrf() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .header("Origin", "https://nova-petzlife-admin.onrender.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF Test 2: Should reject refresh request with untrusted Origin")
    void shouldRejectUntrustedOriginCsrf() throws Exception {
        // End-to-end rejection via security chain (blocked with 403 Forbidden)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "sample_token"))
                        .header("Origin", "https://evil-malicious-site.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());

        // Direct filter validation ensuring fail-closed defense even if CORS filter were bypassed
        AuthCookieCsrfFilter filter = new AuthCookieCsrfFilter(List.of("https://nova-petzlife-admin.onrender.com"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Origin", "https://evil-malicious-site.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("untrusted origin"));
    }

    @Test
    @DisplayName("CSRF Test 3: Should reject refresh request with missing Origin and missing Referer")
    void shouldRejectMissingOriginCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "sample_token"))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("missing Origin header")));
    }

    @Test
    @DisplayName("CSRF Test 4: Should fail closed when trusted-origin configuration is empty")
    void shouldFailClosedWhenOriginsEmpty() throws Exception {
        AuthCookieCsrfFilter emptyFilter = new AuthCookieCsrfFilter(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Origin", "https://nova-petzlife-admin.onrender.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        emptyFilter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("untrusted origin"));
    }

    @Test
    @DisplayName("CSRF Test 5: Should reject refresh request with missing X-Requested-With header")
    void shouldRejectMissingXRequestedWithCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "sample_token"))
                        .header("Origin", "https://nova-petzlife-admin.onrender.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("missing or invalid X-Requested-With header")));
    }

    @Test
    @DisplayName("CSRF Test 6: Valid preflight OPTIONS followed by valid refresh request")
    void shouldPermitPreflightFollowedByRefresh() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");

        // Preflight OPTIONS request
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", "https://nova-petzlife-admin.onrender.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Requested-With"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nova-petzlife-admin.onrender.com"));

        // Subsequent POST refresh request
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .header("Origin", "https://nova-petzlife-admin.onrender.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF Test 7: Valid logout request with trusted origin and X-Requested-With")
    void shouldPermitValidLogout() throws Exception {
        LoginRequest loginReq = new LoginRequest("test@gmail.com", "Test@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookie)
                        .header("Origin", "https://nova-petzlife-admin.onrender.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // --- END CSRF TEST MATRIX ---

    @Test
    @DisplayName("Should forbid customer token from accessing admin inventory endpoints")
    void shouldForbidCustomerFromAdminEndpoint() throws Exception {
        String email = "cust_admin_test_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "SecretP@ssword1",
                "Customer",
                "Only",
                "+919876543213"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseJson).path("data").path("token").asText();

        mockMvc.perform(get("/api/v1/admin/inventory/low-stock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should authenticate using 10-digit Indian mobile number")
    void shouldLoginWithMobileNumber() throws Exception {
        String phone = "98" + (System.currentTimeMillis() % 100000000L);
        if (phone.length() < 10) {
            phone = phone + "0".repeat(10 - phone.length());
        }
        String email = "mobile_user_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "StrongPass@2026",
                "Rahul",
                "Varma",
                phone
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest(phone, "StrongPass@2026");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()));
    }

    @Test
    @DisplayName("Should reject invalid login identifier format (neither email nor 10-digit mobile)")
    void shouldRejectInvalidLoginIdentifier() throws Exception {
        LoginRequest invalidEmailReq = new LoginRequest("testgmail.com", "Password@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailReq)))
                .andExpect(status().isBadRequest());

        LoginRequest shortNumberReq = new LoginRequest("12345", "Password@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortNumberReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject weak passwords during registration")
    void shouldRejectWeakPasswords() throws Exception {
        RegisterRequest weak1 = new RegisterRequest("weak1@novapetzlife.com", "test1234", "A", "B", "9876543290");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak1)))
                .andExpect(status().isBadRequest());

        RegisterRequest weak2 = new RegisterRequest("weak2@novapetzlife.com", "Test1234", "A", "B", "9876543291");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak2)))
                .andExpect(status().isBadRequest());

        RegisterRequest weak3 = new RegisterRequest("weak3@novapetzlife.com", "TEST@123", "A", "B", "9876543292");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak3)))
                .andExpect(status().isBadRequest());

        RegisterRequest weak4 = new RegisterRequest("weak4@novapetzlife.com", "Test@abc", "A", "B", "9876543293");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak4)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should authenticate seeded user anand.nair@example.com with Test@123")
    void shouldLoginSeededCustomer() throws Exception {
        LoginRequest loginReq = new LoginRequest("anand.nair@example.com", "Test@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("anand.nair@example.com"));
    }

    @Test
    @DisplayName("Should reject concurrent refresh recovery for deactivated user")
    void shouldRejectConcurrentRefreshForDeactivatedUser() throws Exception {
        String email = "deactivated_refresh_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password@123", "Deact", "Test", "9998887771");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie originalCookie = regResult.getResponse().getCookie("refreshToken");
        assertNotNull(originalCookie, "Original refresh cookie must be present");

        // Rotate once legitimately so original token has replacedByTokenHash set
        MvcResult rotateResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andReturn();
        assertNotNull(rotateResult.getResponse().getCookie("refreshToken"));

        // Now deactivate user
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);

        // Concurrent recovery attempt with original (replaced) cookie during grace period
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("deactivated")));

        // Cleanup
        user.setIsActive(true);
        userRepository.save(user);
    }

    @Test
    @DisplayName("Should fail-closed when CSRF filter has empty or null allowed-origins")
    void shouldFailClosedWhenCsrfOriginsEmpty() throws Exception {
        AuthCookieCsrfFilter filter = new AuthCookieCsrfFilter(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Origin", "http://localhost:4200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("untrusted origin"));
    }

    @Test
    @DisplayName("Should reject untrusted localhost origin when CSRF filter is configured only for production origins")
    void shouldRejectUntrustedOriginInProductionCsrfFilter() throws Exception {
        AuthCookieCsrfFilter filter = new AuthCookieCsrfFilter(List.of(
                "https://nova-petzlife.onrender.com",
                "https://nova-petzlife-admin.onrender.com"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Origin", "http://localhost:4200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("untrusted origin"));
    }
}
