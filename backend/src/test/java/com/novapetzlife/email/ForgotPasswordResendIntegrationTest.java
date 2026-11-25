package com.novapetzlife.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.auth.ForgotPasswordRequest;
import com.novapetzlife.auth.PasswordResetToken;
import com.novapetzlife.auth.PasswordResetTokenRepository;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForgotPasswordResendIntegrationTest {

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

    private User testCustomer;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() ->
                roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer"))
        );
        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN").orElseGet(() ->
                roleRepository.save(new Role("ROLE_SUPER_ADMIN", "Super admin"))
        );

        String custEmail = "cust_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testCustomer = new User(custEmail, passwordEncoder.encode("Pass123!"), "Customer", "User", "9876500001");
        testCustomer.setIsActive(true);
        testCustomer.setRoles(Set.of(customerRole));
        testCustomer = userRepository.save(testCustomer);

        String adminEmail = "admin_" + UUID.randomUUID().toString().substring(0, 8) + "@novapetzlife.com";
        testAdmin = new User(adminEmail, passwordEncoder.encode("Pass123!"), "Admin", "User", "9876500002");
        testAdmin.setIsActive(true);
        testAdmin.setRoles(Set.of(superAdminRole));
        testAdmin = userRepository.save(testAdmin);
    }

    @Test
    @DisplayName("Registered customer receives generic response and triggers ResendEmailService dispatch")
    void testForgotPassword_RegisteredCustomer_DispatchesResendEmail() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(testCustomer.getEmail());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("password reset link has been sent")));

        verify(resendEmailService, times(1)).sendPasswordResetEmail(
                eq(testCustomer.getEmail()),
                eq("Customer"),
                anyString()
        );

        // Verify token is securely hashed in the database
        List<PasswordResetToken> tokens = resetTokenRepository.findByUser(testCustomer);
        assertFalse(tokens.isEmpty());
        PasswordResetToken latest = tokens.get(tokens.size() - 1);
        assertNotNull(latest.getTokenHash());
        assertFalse(latest.isUsed());
        assertTrue(latest.isValid());
    }

    @Test
    @DisplayName("Unknown email receives identical generic response without invoking ResendEmailService")
    void testForgotPassword_UnknownEmail_GenericResponseWithoutDispatch() throws Exception {
        String unknown = "nonexistent_" + UUID.randomUUID().toString().substring(0, 8) + "@nowhere.org";
        ForgotPasswordRequest req = new ForgotPasswordRequest(unknown);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("password reset link has been sent")));

        verify(resendEmailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Admin email via customer forgot-password endpoint does not dispatch reset token")
    void testForgotPassword_AdminAccount_NotProcessedViaCustomerEndpoint() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(testAdmin.getEmail());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("password reset link has been sent")));

        verify(resendEmailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Repeated requests invalidate previous unconsumed reset tokens")
    void testForgotPassword_RepeatedRequests_InvalidatesEarlierTokens() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(testCustomer.getEmail());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<PasswordResetToken> tokens = resetTokenRepository.findByUser(testCustomer);
        assertTrue(tokens.size() >= 2);
        // Previous token must be marked used or invalid
        PasswordResetToken first = tokens.get(tokens.size() - 2);
        assertTrue(first.isUsed());
    }
}
