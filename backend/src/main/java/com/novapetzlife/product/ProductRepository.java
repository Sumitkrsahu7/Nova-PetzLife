package com.novapetzlife.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
    @Query("SELECT p FROM Product p WHERE p.isFeatured = true AND p.isActive = true AND (p.isArchived = false OR p.isArchived IS NULL)")
    List<Product> findByIsFeaturedTrueAndIsActiveTrue();

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND (p.isArchived = false OR p.isArchived IS NULL)")
    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByIsArchivedTrue(Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.brand b
        LEFT JOIN p.category c
        WHERE p.isActive = true
        AND (p.isArchived = false OR p.isArchived IS NULL)
        AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        ORDER BY p.name ASC
    """)
    List<Product> findSuggestions(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.brand b
        LEFT JOIN p.category c
        WHERE p.isActive = true
        AND (p.isArchived = false OR p.isArchived IS NULL)
        AND (
            (:petType IS NOT NULL AND LOWER(p.petType) = LOWER(:petType))
            OR (:categorySlug IS NOT NULL AND (LOWER(c.slug) = LOWER(:categorySlug) OR LOWER(c.parent.slug) = LOWER(:categorySlug)))
            OR (:brandName IS NOT NULL AND LOWER(b.name) LIKE LOWER(CONCAT('%', :brandName, '%')))
            OR (:keyword IS NOT NULL AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.productType) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ))
        )
        ORDER BY p.isFeatured DESC, p.id DESC
    """)
    List<Product> findSimilarProducts(
            @Param("petType") String petType,
            @Param("categorySlug") String categorySlug,
            @Param("brandName") String brandName,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Product p
        WHERE p.isActive = true
        AND (p.isArchived = false OR p.isArchived IS NULL)
        ORDER BY p.isFeatured DESC, p.id DESC
    """)
    List<Product> findTopActiveProducts(Pageable pageable);
}
