package com.novapetzlife.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliverySettingsRepository extends JpaRepository<DeliverySettings, Long> {
    Optional<DeliverySettings> findFirstByOrderByIdAsc();
}
