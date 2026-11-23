package com.novapetzlife.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUserId(Long userId);

    List<Customer> findAllByEmailIgnoreCase(String email);

    Optional<Customer> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    default Optional<Customer> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return findFirstByEmailIgnoreCaseOrderByIdAsc(email.trim());
    }

    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findFirstByOrderByIdAsc();

    @Query("SELECT c FROM Customer c WHERE :query IS NULL OR :query = '' " +
           "OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR c.phone LIKE CONCAT('%', :query, '%')")
    Page<Customer> searchCustomers(@Param("query") String query, Pageable pageable);
}
