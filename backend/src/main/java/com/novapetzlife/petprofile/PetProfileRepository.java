package com.novapetzlife.petprofile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetProfileRepository extends JpaRepository<PetProfile, Long> {

    List<PetProfile> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<PetProfile> findByCustomerIdAndPetType(Long customerId, String petType);

    @Query("SELECT p FROM PetProfile p WHERE p.customer.id = :customerId " +
           "OR (:userId IS NOT NULL AND p.customer.user.id = :userId) " +
           "ORDER BY p.createdAt DESC")
    List<PetProfile> findCustomerPets(@Param("customerId") Long customerId, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT p FROM PetProfile p WHERE " +
           "(:petType IS NULL OR :petType = '' OR LOWER(p.petType) = LOWER(:petType)) AND " +
           "(:query IS NULL OR :query = '' OR " +
           " LOWER(p.petName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.customer.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.customer.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.customer.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.customer.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<PetProfile> searchForAdmin(@Param("query") String query, @Param("petType") String petType, Pageable pageable);
}
