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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetGmailBridgeIntegrationTest {

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

    @Autowired
    private EmailService primaryEmailService;

    @MockBean(name = "googleAppsScriptEmailService")
    private GoogleAppsScriptEmailService gmailBridgeEmailService;

    @MockBean
    private ResendEmailService resendEmailService;

    private User testCustomer;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        when(gmailBridgeEmailService.isEnabled()).thenReturn(true);

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() ->
                roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer"))
        );
        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN").orElseGet(() ->
                roleRepository.save(new Role("ROLE_SUPER_ADMIN", "Super admin"))
        );

        String custEmail = "bridge_cust_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        testCustomer = new User(custEmail, passwordEncoder.encode("Pass123!"), "Priya", "Customer", "9876511111");
        testCustomer.setIsActive(true);
        testCustomer.setRoles(Set.of(customerRole));
        testCustomer = userRepository.save(testCustomer);

        String adminEmail = "bridge_admin_" + UUID.randomUUID().toString().substring(0, 8) + "@novapetzlife.com";
        testAdmin = new User(adminEmail, passwordEncoder.encode("Pass123!"), "Admin", "User", "9876511112");
        testAdmin.setIsActive(true);
        testAdmin.setRoles(Set.of(superAdminRole));
        testAdmin = userRepository.save(testAdmin);
    }

    @Test
    @DisplayName("Primary EmailService bean remains ResendEmailService so existing transactional notifications are unaffected")
    void primaryEmailServiceRemainsResend() {
        assertSame(resendEmailService, primaryEmailService);
    }

    @Test
    @DisplayName("When Gmail bridge is enabled, forgot-password dispatches via GoogleAppsScriptEmailService without invoking Resend")
    void forgotPassword_WhenGmailBridgeEnabled_RoutesOnlyToGmailBridge() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(testCustomer.getEmail());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("password reset link has been sent")));

        verify(gmailBridgeEmailService, times(1)).sendPasswordResetEmail(
                eq(testCustomer.getEmail()),
                eq("Priya"),
                anyString()
        );
        verify(resendEmailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());

        List<PasswordResetToken> tokens = resetTokenRepository.findByUser(testCustomer);
        assertFalse(tokens.isEmpty());
        assertTrue(tokens.get(tokens.size() - 1).isValid());
    }

    @Test
    @DisplayName("Unknown and admin emails do not trigger Gmail bridge dispatch")
    void forgotPassword_UnknownOrAdmin_NeverDispatches() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("nobody@gmail.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(testAdmin.getEmail()))))
                .andExpect(status().isOk());

        verify(gmailBridgeEmailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }
}
