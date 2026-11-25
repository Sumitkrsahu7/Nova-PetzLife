package com.novapetzlife.auth;

import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.security.JwtTokenProvider;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserOAuthIdentityRepository;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private GoogleOAuthService googleOAuthService;
    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;
    @Mock
    private com.novapetzlife.customer.CustomerRepository customerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private PlatformBootstrapLockRepository bootstrapLockRepository;

    private AuthService authService;
    private Role superAdminRole;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                userRepository,
                roleRepository,
                passwordEncoder,
                tokenProvider,
                refreshTokenService,
                googleOAuthService,
                userOAuthIdentityRepository,
                customerRepository,
                customerService,
                bootstrapLockRepository
        );

        superAdminRole = new Role("ROLE_SUPER_ADMIN", "Full platform access");
    }

    @Test
    @DisplayName("bootstrapSuperAdmin succeeds when zero active Super Admins exist and no bootstrap lock is present")
    void bootstrapSuperAdmin_Success_WhenNoSuperAdminExists() {
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);
        when(userRepository.existsByEmail("admin@novapetzlife.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("bcrypt-encoded-password");

        User savedUser = new User();
        savedUser.setId(100L);
        savedUser.setEmail("admin@novapetzlife.com");
        savedUser.setFirstName("Super");
        savedUser.setLastName("Admin");
        savedUser.getRoles().add(superAdminRole);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateToken(any())).thenReturn("mocked.jwt.token");
        RefreshToken dummyToken = new RefreshToken(savedUser, "tokenhash", Instant.now().plusSeconds(3600), "127.0.0.1");
        when(refreshTokenService.createRefreshToken(any(), any()))
                .thenReturn(new RefreshTokenService.TokenPair("mock-refresh-token", dummyToken));

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "admin@novapetzlife.com",
                "StrongPassword123!",
                "Super",
                "Admin",
                "9876543210",
                null
        );

        AuthResult result = authService.bootstrapSuperAdmin(request, "127.0.0.1");

        assertNotNull(result);
        assertEquals("mocked.jwt.token", result.response().token());
        assertEquals("admin@novapetzlife.com", result.response().email());
        assertEquals("mock-refresh-token", result.rawRefreshToken());
        assertTrue(result.response().roles().contains("ROLE_SUPER_ADMIN"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertEquals("admin@novapetzlife.com", captured.getEmail());
        assertEquals("bcrypt-encoded-password", captured.getPasswordHash());
        assertTrue(captured.getIsActive());
        assertTrue(captured.getRoles().contains(superAdminRole));
        verify(customerService).getOrCreateCustomerForUser(savedUser);
        verify(bootstrapLockRepository).save(any(PlatformBootstrapLock.class));
    }

    @Test
    @DisplayName("bootstrapSuperAdmin fails when persistent platform bootstrap lock is already set (even if count is 0)")
    void bootstrapSuperAdmin_ThrowsIllegalStateException_WhenBootstrapLockAlreadyExists() {
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(true);

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "newadmin@novapetzlife.com",
                "StrongPassword123!",
                "Super",
                "Admin",
                null,
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.bootstrapSuperAdmin(request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Initial Super Admin provisioning has already been completed"));
        verify(userRepository, never()).save(any());
        verify(bootstrapLockRepository, never()).save(any());
    }

    @Test
    @DisplayName("bootstrapSuperAdmin fails when shared secret is configured but request provides invalid secret")
    void bootstrapSuperAdmin_ThrowsBadCredentials_WhenSecretMismatch() {
        ReflectionTestUtils.setField(authService, "configuredBootstrapSecret", "correct-ultra-secret");
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "admin@novapetzlife.com",
                "StrongPassword123!",
                "Super",
                "Admin",
                null,
                "wrong-secret"
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                authService.bootstrapSuperAdmin(request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Invalid bootstrap secret"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("bootstrapSuperAdmin succeeds when shared secret is configured and matches")
    void bootstrapSuperAdmin_Success_WhenSecretMatches() {
        ReflectionTestUtils.setField(authService, "configuredBootstrapSecret", "correct-ultra-secret");
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);
        when(userRepository.existsByEmail("admin@novapetzlife.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("bcrypt-encoded-password");

        User savedUser = new User();
        savedUser.setId(100L);
        savedUser.setEmail("admin@novapetzlife.com");
        savedUser.setFirstName("Super");
        savedUser.setLastName("Admin");
        savedUser.getRoles().add(superAdminRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateToken(any())).thenReturn("mocked.jwt.token");
        RefreshToken dummyToken = new RefreshToken(savedUser, "tokenhash", Instant.now().plusSeconds(3600), "127.0.0.1");
        when(refreshTokenService.createRefreshToken(any(), any()))
                .thenReturn(new RefreshTokenService.TokenPair("mock-refresh-token", dummyToken));

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "admin@novapetzlife.com",
                "StrongPassword123!",
                "Super",
                "Admin",
                null,
                "correct-ultra-secret"
        );

        AuthResult result = authService.bootstrapSuperAdmin(request, "127.0.0.1");
        assertNotNull(result);
        verify(bootstrapLockRepository).save(any(PlatformBootstrapLock.class));
    }

    @Test
    @DisplayName("bootstrapSuperAdmin fails with IllegalStateException when active Super Admin already exists")
    void bootstrapSuperAdmin_ThrowsIllegalStateException_WhenSuperAdminExists() {
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(1L);

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "another.admin@novapetzlife.com",
                "StrongPassword123!",
                "Another",
                "Admin",
                null,
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.bootstrapSuperAdmin(request, "192.168.1.1")
        );

        assertTrue(ex.getMessage().contains("Bootstrap disabled: An active Super Admin account already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("bootstrapSuperAdmin fails with IllegalArgumentException when email already exists")
    void bootstrapSuperAdmin_ThrowsIllegalArgumentException_WhenEmailExists() {
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);
        when(userRepository.existsByEmail("existing@novapetzlife.com")).thenReturn(true);

        BootstrapSuperAdminRequest request = new BootstrapSuperAdminRequest(
                "existing@novapetzlife.com",
                "StrongPassword123!",
                "Super",
                "Admin",
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.bootstrapSuperAdmin(request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Email is already registered: existing@novapetzlife.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("SuperAdminBootstrapRunner provisions Super Admin when zero exist, no lock present, and env vars are configured")
    void runner_ProvisionsAdmin_WhenZeroExistAndEnvVarsPresent() {
        SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                userRepository, roleRepository, passwordEncoder, customerService, bootstrapLockRepository
        );
        ReflectionTestUtils.setField(runner, "bootstrapEmail", "envadmin@novapetzlife.com");
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "EnvPassSecret123!");
        ReflectionTestUtils.setField(runner, "bootstrapFirstName", "EnvAdmin");
        ReflectionTestUtils.setField(runner, "bootstrapLastName", "Root");
        ReflectionTestUtils.setField(runner, "bootstrapPhone", "9998887776");

        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);
        when(userRepository.existsByEmail("envadmin@novapetzlife.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(passwordEncoder.encode("EnvPassSecret123!")).thenReturn("bcrypt-runner-pass");

        User savedUser = new User();
        savedUser.setId(201L);
        savedUser.setEmail("envadmin@novapetzlife.com");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        ApplicationArguments args = mock(ApplicationArguments.class);
        runner.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertEquals("envadmin@novapetzlife.com", created.getEmail());
        assertEquals("bcrypt-runner-pass", created.getPasswordHash());
        assertEquals("EnvAdmin", created.getFirstName());
        assertEquals("Root", created.getLastName());
        assertEquals("9998887776", created.getPhone());
        assertTrue(created.getRoles().contains(superAdminRole));
        verify(customerService).getOrCreateCustomerForUser(savedUser);
        verify(bootstrapLockRepository).save(any(PlatformBootstrapLock.class));
    }

    @Test
    @DisplayName("SuperAdminBootstrapRunner permanently skips when platform bootstrap lock is already set")
    void runner_Skips_WhenBootstrapLockAlreadySet() {
        SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                userRepository, roleRepository, passwordEncoder, customerService, bootstrapLockRepository
        );
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(true);

        ApplicationArguments args = mock(ApplicationArguments.class);
        runner.run(args);

        verify(userRepository, never()).countActiveSuperAdmins();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("SuperAdminBootstrapRunner skips provisioning when active Super Admin exists")
    void runner_Skips_WhenActiveAdminExists() {
        SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                userRepository, roleRepository, passwordEncoder, customerService, bootstrapLockRepository
        );
        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(2L);

        ApplicationArguments args = mock(ApplicationArguments.class);
        runner.run(args);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("SuperAdminBootstrapRunner skips gracefully when env vars are blank")
    void runner_Skips_WhenEnvVarsBlank() {
        SuperAdminBootstrapRunner runner = new SuperAdminBootstrapRunner(
                userRepository, roleRepository, passwordEncoder, customerService, bootstrapLockRepository
        );
        ReflectionTestUtils.setField(runner, "bootstrapEmail", "");
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "");

        when(bootstrapLockRepository.existsByBootstrapKey("SUPER_ADMIN_BOOTSTRAP")).thenReturn(false);
        when(userRepository.countActiveSuperAdmins()).thenReturn(0L);

        ApplicationArguments args = mock(ApplicationArguments.class);
        runner.run(args);

        verify(userRepository, never()).save(any());
    }
}
