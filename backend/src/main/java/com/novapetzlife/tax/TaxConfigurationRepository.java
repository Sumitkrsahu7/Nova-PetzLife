package com.novapetzlife.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, Long> {
    Optional<TaxConfiguration> findByHsnCode(String hsnCode);
    List<TaxConfiguration> findByIsActiveTrue();
}
