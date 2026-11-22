package com.novapetzlife.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOAuthIdentityRepository extends JpaRepository<UserOAuthIdentity, Long> {

    Optional<UserOAuthIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    Optional<UserOAuthIdentity> findByUserIdAndProvider(Long userId, String provider);

    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
