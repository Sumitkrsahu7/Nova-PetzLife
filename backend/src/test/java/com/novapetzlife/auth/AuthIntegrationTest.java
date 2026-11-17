package com.novapetzlife.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully register a new customer and return JWT")
    void shouldRegisterNewCustomer() throws Exception {
        String email = "customer_" + System.currentTimeMillis() + "@novapetzlife.com";
        RegisterRequest request = new RegisterRequest(
                email,
                "StrongPassword123!",
                "Anand",
                "Nair",
                "+919876543210"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.firstName").value("Anand"))
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_CUSTOMER")));
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

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Duplicate registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should authenticate user and issue JWT token on valid login")
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

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()));
    }

    @Test
    @DisplayName("Should reject unauthorized access to protected admin endpoints")
    void shouldRejectUnauthorizedAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isForbidden());
    }

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

        // Login using mobile number
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
        // Missing uppercase and special char
        RegisterRequest weak1 = new RegisterRequest("weak1@novapetzlife.com", "test1234", "A", "B", "9876543290");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak1)))
                .andExpect(status().isBadRequest());

        // Missing special char
        RegisterRequest weak2 = new RegisterRequest("weak2@novapetzlife.com", "Test1234", "A", "B", "9876543291");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak2)))
                .andExpect(status().isBadRequest());

        // Missing lowercase
        RegisterRequest weak3 = new RegisterRequest("weak3@novapetzlife.com", "TEST@123", "A", "B", "9876543292");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak3)))
                .andExpect(status().isBadRequest());

        // Missing number
        RegisterRequest weak4 = new RegisterRequest("weak4@novapetzlife.com", "Test@abc", "A", "B", "9876543293");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak4)))
                .andExpect(status().isBadRequest());
    }
}
