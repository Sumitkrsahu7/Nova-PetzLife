package com.novapetzlife.auth;

import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Controlled startup runner that provisions the initial Super Admin account
 * from environment variables (BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD)
 * ONLY IF zero active Super Admins exist in the database.
 *
 * Ensures idempotent, one-time bootstrap on cloud platforms (e.g. Render) without manual SQL.
 */
@Component
public class SuperAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final PlatformBootstrapLockRepository bootstrapLockRepository;

    @Value("${app.bootstrap.admin-email:${BOOTSTRAP_ADMIN_EMAIL:}}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.admin-password:${BOOTSTRAP_ADMIN_PASSWORD:}}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.admin-first-name:${BOOTSTRAP_ADMIN_FIRST_NAME:Super}}")
    private String bootstrapFirstName;

    @Value("${app.bootstrap.admin-last-name:${BOOTSTRAP_ADMIN_LAST_NAME:Admin}}")
    private String bootstrapLastName;

    @Value("${app.bootstrap.admin-phone:${BOOTSTRAP_ADMIN_PHONE:}}")
    private String bootstrapPhone;

    public SuperAdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            CustomerService customerService,
            PlatformBootstrapLockRepository bootstrapLockRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.bootstrapLockRepository = bootstrapLockRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapLockRepository.existsByBootstrapKey(AuthService.BOOTSTRAP_LOCK_KEY)) {
            log.debug("[BOOTSTRAP RUNNER] Platform bootstrap lock already set; startup bootstrap permanently skipped.");
            return;
        }

        long activeSuperAdmins = userRepository.countActiveSuperAdmins();
        if (activeSuperAdmins > 0) {
            log.debug("[BOOTSTRAP RUNNER] {} active Super Admin(s) detected; startup bootstrap skipped.", activeSuperAdmins);
            return;
        }

        if (!StringUtils.hasText(bootstrapEmail) || !StringUtils.hasText(bootstrapPassword)) {
            log.info("[BOOTSTRAP RUNNER] Zero active Super Admins found. Set BOOTSTRAP_ADMIN_EMAIL/PASSWORD in environment or call POST /api/v1/auth/bootstrap-super-admin to initialize.");
            return;
        }

        String email = bootstrapEmail.toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("[BOOTSTRAP RUNNER] User {} already exists; cannot bootstrap as Super Admin.", email);
            return;
        }

        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN").orElse(null);
        if (superAdminRole == null) {
            log.error("[BOOTSTRAP RUNNER] ROLE_SUPER_ADMIN role not found in database; cannot provision Super Admin.");
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        user.setFirstName(bootstrapFirstName.trim());
        user.setLastName(StringUtils.hasText(bootstrapLastName) ? bootstrapLastName.trim() : "");
        user.setPhone(StringUtils.hasText(bootstrapPhone) ? bootstrapPhone.trim() : null);
        user.setIsActive(true);
        user.getRoles().add(superAdminRole);

        User savedUser = userRepository.save(user);
        customerService.getOrCreateCustomerForUser(savedUser);

        // Record persistent atomic one-time completion lock
        bootstrapLockRepository.save(new PlatformBootstrapLock(AuthService.BOOTSTRAP_LOCK_KEY, "127.0.0.1", email));

        log.info("[BOOTSTRAP RUNNER SUCCESS] Authoritative initial Super Admin account {} successfully bootstrapped on startup.", email);
    }
}
