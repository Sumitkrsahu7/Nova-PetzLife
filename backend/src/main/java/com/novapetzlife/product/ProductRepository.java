package com.novapetzlife.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);
    List<Product> findByIsFeaturedTrueAndIsActiveTrue();
    Page<Product> findByIsActiveTrue(Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.brand b
        LEFT JOIN p.category c
        WHERE p.isActive = true
        AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        ORDER BY p.name ASC
    """)
    List<Product> findSuggestions(@Param("query") String query, Pageable pageable);
}
