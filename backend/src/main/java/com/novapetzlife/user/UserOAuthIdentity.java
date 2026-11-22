package com.novapetzlife.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_oauth_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_oauth_provider_subject", columnNames = {"provider", "provider_subject"})
})
public class UserOAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String provider; // e.g. "GOOGLE"

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject; // stable 'sub' claim from Google ID token

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public UserOAuthIdentity() {}

    public UserOAuthIdentity(User user, String provider, String providerSubject, String email) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
