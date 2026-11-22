package com.novapetzlife.auth;

import com.novapetzlife.security.CustomUserDetailsService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GoogleOAuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User googleOnlyUser;
    private User localUser;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer account")));

        String uuidG = UUID.randomUUID().toString().substring(0, 8);
        googleOnlyUser = new User();
        googleOnlyUser.setEmail("google_user_" + uuidG + "@gmail.com");
        googleOnlyUser.setPasswordHash(null); // Explicit OAuth-only user with no local password
        googleOnlyUser.setFirstName("Google");
        googleOnlyUser.setLastName("User");
        googleOnlyUser.setIsActive(true);
        googleOnlyUser.setRoles(Set.of(customerRole));
        googleOnlyUser = userRepository.save(googleOnlyUser);

        String uuidL = UUID.randomUUID().toString().substring(0, 8);
        localUser = new User();
        localUser.setEmail("local_user_" + uuidL + "@test.com");
        localUser.setPasswordHash(passwordEncoder.encode("LocalSecretPass123!"));
        localUser.setFirstName("Local");
        localUser.setLastName("User");
        localUser.setPhone("98473" + uuidL.substring(0, 5));
        localUser.setIsActive(true);
        localUser.setRoles(Set.of(customerRole));
        localUser = userRepository.save(localUser);
    }

    @Test
    @DisplayName("Google-only account with null passwordHash loads safely in UserDetailsService without throwing IllegalArgumentException")
    void googleOnlyAccount_loadsSafelyInUserDetailsService() {
        assertDoesNotThrow(() -> {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(googleOnlyUser.getEmail());
            assertNotNull(userDetails);
            assertEquals(googleOnlyUser.getEmail(), userDetails.getUsername());
            assertNotNull(userDetails.getPassword(), "UserDetails password must not be null");
            assertEquals(CustomUserDetailsService.OAUTH_NO_LOCAL_PASSWORD, userDetails.getPassword());
        });
    }

    @Test
    @DisplayName("Password login attempt against Google-only account throws BadCredentialsException (HTTP 401) and NOT HTTP 500")
    void passwordLoginAttemptAgainstGoogleAccount_failsWithBadCredentials() {
        LoginRequest req = new LoginRequest(googleOnlyUser.getEmail(), "AnyPassword123!");
        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> {
            authService.login(req, "127.0.0.1");
        });
        assertTrue(ex.getMessage().contains("Google Sign-In") || ex.getMessage().contains("Google"));
    }

    @Test
    @DisplayName("HTTP MockMvc: /api/v1/auth/login against Google-only account returns sanitized HTTP 401 response")
    void httpLoginEndpoint_rejectsOAuthOnlyAccountWith401() throws Exception {
        String jsonPayload = String.format("{\"email\":\"%s\",\"password\":\"AnyPassword123!\"}", googleOnlyUser.getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Google Sign-In")));
    }

    @Test
    @DisplayName("HTTP MockMvc: /api/v1/auth/login with local BCrypt account succeeds with HTTP 200")
    void httpLoginEndpoint_localAccountWithPassword_canLoginSuccessfully() throws Exception {
        String jsonPayload = String.format("{\"email\":\"%s\",\"password\":\"LocalSecretPass123!\"}", localUser.getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(localUser.getEmail()))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("Existing local account with password can still authenticate successfully via service")
    void localAccountWithPassword_canLoginSuccessfully() {
        LoginRequest req = new LoginRequest(localUser.getEmail(), "LocalSecretPass123!");
        var result = authService.login(req, "127.0.0.1");
        assertNotNull(result);
        assertNotNull(result.response().token());
        assertEquals(localUser.getEmail(), result.response().email());
    }

    @Test
    @DisplayName("Local account with incorrect password fails with BadCredentialsException")
    void localAccountWithIncorrectPassword_failsWithBadCredentials() {
        LoginRequest req = new LoginRequest(localUser.getEmail(), "WrongPassword!");
        assertThrows(BadCredentialsException.class, () -> {
            authService.login(req, "127.0.0.1");
        });
    }

    @Test
    @DisplayName("Anonymous user cannot call Google account linking endpoint (requires authentication)")
    void anonymousUser_cannotCallGoogleLinkEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/link")
                        .contentType("application/json")
                        .content("{\"credential\":\"mock-token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
