package com.novapetzlife.auth;

import com.novapetzlife.security.JwtTokenProvider;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        String normalizedPhone = identifier.startsWith("+91") ? identifier.substring(3) : identifier;
        User user = userRepository.findByEmail(identifier.toLowerCase())
                .or(() -> userRepository.findByPhone(identifier))
                .or(() -> userRepository.findByPhone(normalizedPhone))
                .or(() -> userRepository.findByPhone("+91" + normalizedPhone))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        return AuthResponse.of(
                jwt,
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                List.of("ROLE_CUSTOMER")
        );
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

        String jwt = tokenProvider.generateToken(auth);

        return AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles);
    }
}
