package com.novapetzlife.product;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.category.Category;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSearchSpecification {

    public static Specification<Product> filter(
            String query,
            String categorySlug,
            String brandSlug,
            String petType,
            String lifeStage,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStockOnly) {

        return (Root<Product> root, CriteriaQuery<?> cq, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only active and non-archived products for customer catalog & search
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.or(cb.isNull(root.get("isArchived")), cb.isFalse(root.get("isArchived"))));

            // 1. Text Search across name, description, SKU, brand name, category name
            if (query != null && !query.trim().isEmpty()) {
                String cleanQuery = "%" + query.trim().toLowerCase() + "%";

                Join<Product, Brand> brandJoin = root.join("brand", JoinType.LEFT);
                Join<Product, Category> categoryJoin = root.join("category", JoinType.LEFT);
                Join<Category, Category> parentCategoryJoin = categoryJoin.join("parent", JoinType.LEFT);

                Predicate nameMatch = cb.like(cb.lower(root.get("name")), cleanQuery);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), cleanQuery);
                Predicate skuMatch = cb.like(cb.lower(root.get("sku")), cleanQuery);
                Predicate brandMatch = cb.like(cb.lower(brandJoin.get("name")), cleanQuery);
                Predicate categoryMatch = cb.like(cb.lower(categoryJoin.get("name")), cleanQuery);
                Predicate parentCatMatch = cb.like(cb.lower(parentCategoryJoin.get("name")), cleanQuery);
                Predicate petTypeMatch = cb.like(cb.lower(root.get("petType")), cleanQuery);

                predicates.add(cb.or(nameMatch, descMatch, skuMatch, brandMatch, categoryMatch, parentCatMatch, petTypeMatch));
            }

            // 2. Strict Category Hierarchy Filter (match category, parent category, grandparent, or category alias)
            if (categorySlug != null && !categorySlug.trim().isEmpty()) {
                String cleanCat = categorySlug.trim().toLowerCase();
                Join<Product, Category> catJoin = root.join("category", JoinType.INNER);
                Join<Category, Category> parentCatJoin = catJoin.join("parent", JoinType.LEFT);
                Join<Category, Category> grandParentCatJoin = parentCatJoin.join("parent", JoinType.LEFT);

                Predicate directCategory = cb.equal(catJoin.get("slug"), cleanCat);
                Predicate parentCategory = cb.equal(parentCatJoin.get("slug"), cleanCat);
                Predicate grandParentCategory = cb.equal(grandParentCatJoin.get("slug"), cleanCat);

                List<Predicate> catOrPredicates = new ArrayList<>();
                catOrPredicates.add(directCategory);
                catOrPredicates.add(parentCategory);
                catOrPredicates.add(grandParentCategory);

                if ("cat-food".equals(cleanCat) || "cat".equals(cleanCat) || "cats".equals(cleanCat)) {
                    catOrPredicates.add(cb.equal(cb.upper(root.get("petType")), "CAT"));
                } else if ("dog-food".equals(cleanCat) || "dog".equals(cleanCat) || "dogs".equals(cleanCat)) {
                    catOrPredicates.add(cb.equal(cb.upper(root.get("petType")), "DOG"));
                } else if ("grooming".equals(cleanCat) || "grooming-hygiene".equals(cleanCat) || "hygiene".equals(cleanCat)) {
                    catOrPredicates.add(cb.like(cb.lower(catJoin.get("name")), "%grooming%"));
                    catOrPredicates.add(cb.like(cb.lower(catJoin.get("name")), "%litter%"));
                    catOrPredicates.add(cb.like(cb.lower(catJoin.get("name")), "%clean%"));
                    catOrPredicates.add(cb.equal(cb.lower(root.get("productType")), "grooming"));
                    catOrPredicates.add(cb.equal(cb.lower(root.get("productType")), "litter"));
                }

                predicates.add(cb.or(catOrPredicates.toArray(new Predicate[0])));
            }

            // 3. Strict Brand Filter (Brand isolation)
            if (brandSlug != null && !brandSlug.trim().isEmpty()) {
                String cleanBrand = brandSlug.trim().toLowerCase();
                Join<Product, Brand> brandJoin = root.join("brand", JoinType.INNER);
                Predicate brandSlugMatch = cb.equal(brandJoin.get("slug"), cleanBrand);
                Predicate brandNameMatch = cb.equal(cb.lower(brandJoin.get("name")), cleanBrand.replace("-", " "));
                predicates.add(cb.or(brandSlugMatch, brandNameMatch));
            }

            // 4. Pet Type (Cat, Dog, Bird, etc.)
            if (petType != null && !petType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("petType")), petType.trim().toLowerCase()));
            }

            // 5. Life Stage (Kitten, Puppy, Adult, Senior)
            if (lifeStage != null && !lifeStage.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("lifeStage")), lifeStage.trim().toLowerCase()));
            }

            // 6. Price Range
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        cb.coalesce(root.get("discountedPrice"), root.get("basePrice")),
                        minPrice
                ));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        cb.coalesce(root.get("discountedPrice"), root.get("basePrice")),
                        maxPrice
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
