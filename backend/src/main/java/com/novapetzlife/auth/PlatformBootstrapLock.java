package com.novapetzlife.auth;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent atomic lock entity guaranteeing that initial Super Admin bootstrap
 * can execute exactly once across the lifetime of the application, across restarts,
 * and immune to subsequent account deletion or disabling.
 */
@Entity
@Table(name = "platform_bootstrap_lock")
public class PlatformBootstrapLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bootstrap_key", nullable = false, unique = true, length = 100)
    private String bootstrapKey;

    @Column(name = "bootstrapped_at", nullable = false)
    private Instant bootstrappedAt = Instant.now();

    @Column(name = "bootstrapped_by_ip", length = 45)
    private String bootstrappedByIp;

    @Column(name = "admin_email", nullable = false, length = 150)
    private String adminEmail;

    public PlatformBootstrapLock() {}

    public PlatformBootstrapLock(String bootstrapKey, String bootstrappedByIp, String adminEmail) {
        this.bootstrapKey = bootstrapKey;
        this.bootstrappedByIp = bootstrappedByIp;
        this.adminEmail = adminEmail;
        this.bootstrappedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getBootstrapKey() {
        return bootstrapKey;
    }

    public Instant getBootstrappedAt() {
        return bootstrappedAt;
    }

    public String getBootstrappedByIp() {
        return bootstrappedByIp;
    }

    public String getAdminEmail() {
        return adminEmail;
    }
}
