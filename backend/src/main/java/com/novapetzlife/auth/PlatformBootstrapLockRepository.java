package com.novapetzlife.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformBootstrapLockRepository extends JpaRepository<PlatformBootstrapLock, Long> {
    boolean existsByBootstrapKey(String bootstrapKey);
    Optional<PlatformBootstrapLock> findByBootstrapKey(String bootstrapKey);
}
