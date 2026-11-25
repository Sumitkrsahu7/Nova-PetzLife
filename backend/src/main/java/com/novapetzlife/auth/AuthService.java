package com.novapetzlife.auth;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.security.JwtTokenProvider;
import com.novapetzlife.user.Permission;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserOAuthIdentity;
import com.novapetzlife.user.UserOAuthIdentityRepository;
import com.novapetzlife.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String BOOTSTRAP_LOCK_KEY = "SUPER_ADMIN_BOOTSTRAP";

    @Value("${app.bootstrap.secret:${BOOTSTRAP_SECRET:}}")
    private String configuredBootstrapSecret;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final GoogleOAuthService googleOAuthService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final PlatformBootstrapLockRepository bootstrapLockRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenService refreshTokenService,
            GoogleOAuthService googleOAuthService,
            UserOAuthIdentityRepository userOAuthIdentityRepository,
            CustomerRepository customerRepository,
            CustomerService customerService,
            PlatformBootstrapLockRepository bootstrapLockRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.googleOAuthService = googleOAuthService;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.bootstrapLockRepository = bootstrapLockRepository;
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResult login(LoginRequest request, String ipAddress) {
        String identifier = request.getIdentifier();

        // Explicitly reject local password authentication for OAuth-only accounts without local password
        String normalizedPhone = identifier.startsWith("+91") ? identifier.substring(3) : identifier;
        Optional<User> targetUser = userRepository.findByEmail(identifier.toLowerCase())
                .or(() -> userRepository.findByPhone(identifier))
                .or(() -> userRepository.findByPhone(normalizedPhone))
                .or(() -> userRepository.findByPhone("+91" + normalizedPhone));

        if (targetUser.isPresent()) {
            User existing = targetUser.get();
            if (!StringUtils.hasText(existing.getPasswordHash())) {
                log.warn("Password login attempted for OAuth-only account: {}", existing.getEmail());
                throw new BadCredentialsException("This account is registered with Google. Please use Google Sign-In to log in.");
            }
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = targetUser.orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        List<String> permissions = user.getPermissions() != null
                ? user.getPermissions().stream().map(Permission::getName).toList()
                : List.of();

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.createRefreshToken(user, ipAddress);
        AuthResponse response = AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);

        return new AuthResult(response, tokenPair.rawToken());
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        return register(request, null);
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String ipAddress) {
        String normalizedEmail = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists: " + normalizedEmail);
        }

        String rawPhone = request.phone().trim();
        String normalizedPhone = rawPhone.startsWith("+91") ? rawPhone.substring(3) : rawPhone;
        if (userRepository.existsByPhone(rawPhone) || userRepository.existsByPhone(normalizedPhone) || userRepository.existsByPhone("+91" + normalizedPhone)) {
            throw new IllegalArgumentException("An account with this phone number already exists: " + rawPhone);
        }

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer account")));

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName() != null ? request.lastName().trim() : "",
                rawPhone
        );
        user.setRoles(Set.of(customerRole));
        User savedUser = userRepository.save(user);

        // Authenticate new user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );
        String jwt = tokenProvider.generateToken(authentication);

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.createRefreshToken(savedUser, ipAddress);
        AuthResponse response = AuthResponse.of(
                jwt,
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                List.of("ROLE_CUSTOMER"),
                List.of()
        );

        return new AuthResult(response, tokenPair.rawToken());
    }

    @Transactional
    public AuthResult loginWithGoogle(String credential, String ipAddress) {
        GoogleTokenPayload payload = googleOAuthService.verifyToken(credential);
        String googleSub = payload.sub();
        String googleEmail = payload.email().toLowerCase().trim();

        // 1. Check if OAuth identity exists for this Google subject
        Optional<UserOAuthIdentity> identityOpt = userOAuthIdentityRepository.findByProviderAndProviderSubject("GOOGLE", googleSub);
        if (identityOpt.isPresent()) {
            User user = identityOpt.get().getUser();
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new BadCredentialsException("This customer account is currently inactive. Please contact support.");
            }
            return issueTokensForUser(user, ipAddress);
        }

        // 2. Check if a local account already exists with this email
        Optional<User> existingUserOpt = userRepository.findByEmail(googleEmail);
        if (existingUserOpt.isPresent()) {
            // Requirement 5.4: Do not automatically link based only on matching email.
            // Require account linking with password verification or explain to user.
            throw new BadCredentialsException(
                    "An account with this email (" + googleEmail + ") already exists. " +
                    "Please log in with your email and password, then link your Google account in account settings."
            );
        }

        // 3. Register brand-new Google customer (Strictly CUSTOMER role, no admin escalation)
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Standard customer account")));

        String firstName = StringUtils.hasText(payload.givenName()) ? payload.givenName() : (StringUtils.hasText(payload.name()) ? payload.name() : "Google");
        String lastName = StringUtils.hasText(payload.familyName()) ? payload.familyName() : "Customer";

        User newUser = new User();
        newUser.setEmail(googleEmail);
        newUser.setPasswordHash(null); // No local password for Google-only authentication
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setPhone(null);
        newUser.setIsActive(true);
        newUser.setRoles(Set.of(customerRole));
        User savedUser = userRepository.save(newUser);

        // Save OAuth Identity mapping first so verified OAuth identity is established
        UserOAuthIdentity identity = new UserOAuthIdentity(savedUser, "GOOGLE", googleSub, googleEmail);
        userOAuthIdentityRepository.save(identity);

        // Associate Customer profile safely (links guest if exactly one unlinked guest exists, or creates new)
        customerService.getOrCreateCustomerForUser(savedUser);

        log.info("Registered and authenticated new customer via Google: {} (sub: {})", googleEmail, googleSub);
        return issueTokensForUser(savedUser, ipAddress);
    }

    @Transactional
    public void linkGoogleAccount(String credential, String currentUserEmail) {
        GoogleTokenPayload payload = googleOAuthService.verifyToken(credential);
        User user = userRepository.findByEmail(currentUserEmail.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentUserEmail));

        Optional<UserOAuthIdentity> existing = userOAuthIdentityRepository.findByProviderAndProviderSubject("GOOGLE", payload.sub());
        if (existing.isPresent()) {
            if (existing.get().getUser().getId().equals(user.getId())) {
                return; // Already linked to this user
            }
            throw new IllegalArgumentException("This Google account is already linked to another user account.");
        }

        UserOAuthIdentity identity = new UserOAuthIdentity(user, "GOOGLE", payload.sub(), payload.email());
        userOAuthIdentityRepository.save(identity);
        log.info("Linked Google account (sub: {}) to user {}", payload.sub(), user.getEmail());
    }

    private AuthResult issueTokensForUser(User user, String ipAddress) {
        var authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) role::getName)
                .collect(java.util.stream.Collectors.toSet());

        if (user.getPermissions() != null) {
            user.getPermissions().forEach(p -> authorities.add(p::getName));
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        String jwt = tokenProvider.generateToken(auth);

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.createRefreshToken(user, ipAddress);

        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = user.getPermissions() != null
                ? user.getPermissions().stream().map(Permission::getName).toList()
                : List.of();

        AuthResponse response = AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);
        return new AuthResult(response, tokenPair.rawToken());
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String ipAddress) {
        RefreshTokenService.TokenPair tokenPair = refreshTokenService.rotateRefreshToken(rawRefreshToken, ipAddress);
        User user = tokenPair.entity().getUser();

        var authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) role::getName)
                .collect(java.util.stream.Collectors.toSet());

        if (user.getPermissions() != null) {
            user.getPermissions().forEach(p -> authorities.add(p::getName));
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        String newJwt = tokenProvider.generateToken(auth);

        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = user.getPermissions() != null
                ? user.getPermissions().stream().map(Permission::getName).toList()
                : List.of();

        AuthResponse response = AuthResponse.of(newJwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);
        return new AuthResult(response, tokenPair.rawToken());
    }

    @Transactional
    public AuthResult bootstrapSuperAdmin(BootstrapSuperAdminRequest request, String ipAddress) {
        if (bootstrapLockRepository.existsByBootstrapKey(BOOTSTRAP_LOCK_KEY)) {
            log.warn("Super Admin bootstrap rejected from IP {}: Platform bootstrap lock is permanently set.", ipAddress);
            throw new IllegalStateException("Bootstrap disabled: Initial Super Admin provisioning has already been completed.");
        }

        long activeSuperAdmins = userRepository.countActiveSuperAdmins();
        if (activeSuperAdmins > 0) {
            log.warn("Super Admin bootstrap attempted from IP {} but an active Super Admin already exists", ipAddress);
            throw new IllegalStateException("Bootstrap disabled: An active Super Admin account already exists in the system.");
        }

        // Validate shared bootstrap secret via constant-time comparison if configured
        if (StringUtils.hasText(configuredBootstrapSecret)) {
            String providedSecret = request.bootstrapSecret();
            if (!StringUtils.hasText(providedSecret)
                    || !java.security.MessageDigest.isEqual(
                            configuredBootstrapSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            providedSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                log.warn("Super Admin bootstrap rejected from IP {}: Invalid or missing bootstrap secret.", ipAddress);
                throw new org.springframework.security.authentication.BadCredentialsException("Invalid bootstrap secret.");
            }
        }

        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered: " + email);
        }

        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_SUPER_ADMIN role definition not found in database."));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName() != null ? request.lastName().trim() : "");
        user.setPhone(request.phone() != null ? request.phone().trim() : null);
        user.setIsActive(true);
        user.getRoles().add(superAdminRole);

        User savedUser = userRepository.save(user);

        // Ensure associated customer profile exists for consistent account model
        customerService.getOrCreateCustomerForUser(savedUser);

        // Record persistent atomic one-time completion lock
        PlatformBootstrapLock lock = new PlatformBootstrapLock(BOOTSTRAP_LOCK_KEY, ipAddress, email);
        bootstrapLockRepository.save(lock);

        log.info("[BOOTSTRAP COMPLETE] Created initial authoritative Super Admin account {} from IP {}", email, ipAddress);
        return issueTokensForUser(savedUser, ipAddress);
    }

    public void logout() {
        logout(null, null);
    }

    public void logout(String rawRefreshToken, String ipAddress) {
        if (rawRefreshToken != null && !rawRefreshToken.trim().isEmpty()) {
            refreshTokenService.revokeRefreshToken(rawRefreshToken, ipAddress);
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new IllegalArgumentException("No authenticated user in session");
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        List<String> permissions = user.getPermissions() != null
                ? user.getPermissions().stream().map(Permission::getName).toList()
                : List.of();

        String jwt = tokenProvider.generateToken(auth);

        return AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);
    }
}
