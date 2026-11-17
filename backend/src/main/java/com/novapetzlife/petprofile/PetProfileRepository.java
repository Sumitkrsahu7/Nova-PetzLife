package com.novapetzlife.petprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetProfileRepository extends JpaRepository<PetProfile, Long> {
    List<PetProfile> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<PetProfile> findByCustomerIdAndPetType(Long customerId, String petType);
}
