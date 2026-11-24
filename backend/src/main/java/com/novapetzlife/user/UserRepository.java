package com.novapetzlife.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = 'ROLE_SUPER_ADMIN' AND u.isActive = true")
    long countActiveSuperAdmins();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'ROLE_SUPER_ADMIN' AND u.isActive = true")
    java.util.List<User> findActiveSuperAdminsForUpdate();
}

