package com.novapetzlife.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopLocationRepository extends JpaRepository<ShopLocation, Long> {
    Optional<ShopLocation> findFirstByOrderByIdAsc();
}
