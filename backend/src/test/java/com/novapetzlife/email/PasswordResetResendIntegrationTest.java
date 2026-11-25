package com.novapetzlife.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.auth.PasswordResetService;
import com.novapetzlife.auth.PasswordResetToken;
import com.novapetzlife.auth.PasswordResetTokenRepository;
import com.novapetzlife.auth.ResetPasswordRequest;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetResendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @MockBean
    private ResendEmailService resendEmailService;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() ->
                roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer"))
        );

        String email = "reset_cust_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testUser = new User(email, passwordEncoder.encode("InitialPass123!"), "Alice", "Customer", "9876543299");
        testUser.setIsActive(true);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Valid token successfully resets password and encodes new password with BCrypt")
    void testPasswordReset_ValidToken_Success() throws Exception {
        String rawToken = PasswordResetService.generateSecureToken();
        String tokenHash = PasswordResetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken(
                testUser, tokenHash, Instant.now().plus(15, ChronoUnit.MINUTES), "127.0.0.1"
        );
        resetTokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewSecurePassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("successfully reset")));

        // Verify password in DB is updated and encoded with BCrypt
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewSecurePassword123!", updated.getPasswordHash()));
        assertFalse(passwordEncoder.matches("InitialPass123!", updated.getPasswordHash()));

        // Verify token is now used
        PasswordResetToken consumedToken = resetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertTrue(consumedToken.isUsed());
    }

    @Test
    @DisplayName("Reused token is rejected with 400 Bad Request")
    void testPasswordReset_ReusedToken_Rejected() throws Exception {
        String rawToken = PasswordResetService.generateSecureToken();
        String tokenHash = PasswordResetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken(
                testUser, tokenHash, Instant.now().plus(15, ChronoUnit.MINUTES), "127.0.0.1"
        );
        token.setUsedAt(Instant.now());
        resetTokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewSecurePassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Expired token is rejected with 400 Bad Request")
    void testPasswordReset_ExpiredToken_Rejected() throws Exception {
        String rawToken = PasswordResetService.generateSecureToken();
        String tokenHash = PasswordResetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken(
                testUser, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES), "127.0.0.1"
        );
        resetTokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewSecurePassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Invalid or tampered token is rejected with 400 Bad Request")
    void testPasswordReset_InvalidToken_Rejected() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("completely_fake_invalid_token_999", "NewSecurePassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Weak password failing validation pattern is rejected")
    void testPasswordReset_WeakPassword_Rejected() throws Exception {
        String rawToken = PasswordResetService.generateSecureToken();
        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "weak");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
