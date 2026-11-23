package com.novapetzlife.product;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.category.CategoryService;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.pricing.PricingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.review.ReviewRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final com.novapetzlife.audit.AuditService auditService;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ReviewRepository reviewRepository;
    private final com.novapetzlife.audit.AuditLogRepository auditLogRepository;

    public ProductService(
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            PricingService pricingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.audit.AuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrderItemRepository orderItemRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CartItemRepository cartItemRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) InventoryTransactionRepository inventoryTransactionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ReviewRepository reviewRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.audit.AuditLogRepository auditLogRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.auditService = auditService;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.reviewRepository = reviewRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDto> searchProducts(
            String query,
            String categorySlug,
            String brandSlug,
            String petType,
            String lifeStage,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStockOnly,
            Pageable pageable) {

        Specification<Product> spec = ProductSearchSpecification.filter(
                query, categorySlug, brandSlug, petType, lifeStage, minPrice, maxPrice, inStockOnly
        );

        Page<Product> page = productRepository.findAll(spec, pageable);

        List<ProductSummaryDto> summaryList = new ArrayList<>();
        for (Product product : page.getContent()) {
            Integer stock = inventoryRepository.findTotalAvailableStockByProductId(product.getId());
            boolean inStock = stock != null && stock > 0;

            if (Boolean.TRUE.equals(inStockOnly) && !inStock) {
                continue;
            }

            summaryList.add(ProductSummaryDto.fromEntity(product, inStock));
        }

        return new PageImpl<>(summaryList, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new com.novapetzlife.common.ResourceNotFoundException("Product not found with slug: " + slug));

        if (Boolean.TRUE.equals(product.getIsArchived()) || !Boolean.TRUE.equals(product.getIsActive())) {
            throw new com.novapetzlife.common.ResourceNotFoundException("Product not found with slug: " + slug);
        }

        Integer stock = inventoryRepository.findTotalAvailableStockByProductId(product.getId());
        return ProductDetailDto.fromEntity(product, stock != null ? stock : 0);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        Integer stock = inventoryRepository.findTotalAvailableStockByProductId(product.getId());
        return ProductDetailDto.fromEntity(product, stock != null ? stock : 0);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getFeaturedProducts() {
        List<Product> products = productRepository.findByIsFeaturedTrueAndIsActiveTrue();
        return products.stream()
                .map(p -> {
                    Integer stock = inventoryRepository.findTotalAvailableStockByProductId(p.getId());
                    return ProductSummaryDto.fromEntity(p, stock != null && stock > 0);
                })
                .toList();
    }

    public List<ProductSuggestionDto> getSearchSuggestions(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String clean = query.trim();
        List<Product> products = productRepository.findSuggestions(clean, PageRequest.of(0, 10));
        return products.stream()
                .map(p -> {
                    String img = p.getImages() != null && !p.getImages().isEmpty()
                            ? p.getImages().stream().filter(ProductImage::getIsPrimary).findFirst()
                            .map(ProductImage::getImageUrl)
                            .orElse(p.getImages().get(0).getImageUrl())
                            : null;
                    BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getBasePrice();
                    return new ProductSuggestionDto(
                            p.getId(),
                            p.getName(),
                            p.getSlug(),
                            p.getCategory() != null ? p.getCategory().getName() : null,
                            p.getBrand() != null ? p.getBrand().getName() : null,
                            price,
                            img
                    );
                })
                .toList();
    }

    @Transactional
    public ProductDetailDto createProduct(ProductCreateRequest request) {
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new IllegalArgumentException("Brand not found with id: " + request.brandId()));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + request.categoryId()));

        pricingService.validatePrice(request.basePrice(), request.discountedPrice());

        String slug = request.slug() != null && !request.slug().isBlank()
                ? CategoryService.toSlug(request.slug())
                : CategoryService.toSlug(request.name());

        if (productRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        String sku = request.sku() != null && !request.sku().isBlank()
                ? request.sku().trim().toUpperCase()
                : "NPZ-" + brand.getSlug().toUpperCase().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Product product = new Product(
                brand,
                category,
                request.name().trim(),
                slug,
                sku,
                request.basePrice(),
                request.discountedPrice()
        );

        product.setDescription(request.description());
        product.setBarcode(request.barcode());
        product.setPetType(request.petType());
        product.setLifeStage(request.lifeStage());
        product.setProductType(request.productType());
        product.setIsFeatured(Boolean.TRUE.equals(request.isFeatured()));

        // Return Policy
        ProductReturnPolicy policy = new ProductReturnPolicy();
        policy.setProduct(product);
        if (request.returnPolicy() != null) {
            ProductCreateRequest.ReturnPolicyCreateRequest reqPol = request.returnPolicy();
            policy.setReturnAllowed(Boolean.TRUE.equals(reqPol.returnAllowed()));
            policy.setReturnWindowDays(reqPol.returnWindowDays() != null ? reqPol.returnWindowDays() : 0);
            policy.setOriginalPackagingRequired(reqPol.originalPackagingRequired() != null ? reqPol.originalPackagingRequired() : true);
            policy.setUnusedConditionRequired(reqPol.unusedConditionRequired() != null ? reqPol.unusedConditionRequired() : true);
            policy.setRefundAllowed(Boolean.TRUE.equals(reqPol.refundAllowed()));
            policy.setRefundMethod(reqPol.refundMethod() != null ? reqPol.refundMethod() : "ORIGINAL_PAYMENT");
            policy.setCustomConditions(reqPol.customConditions());
        } else {
            policy.setReturnAllowed(true);
            policy.setReturnWindowDays(7);
            policy.setRefundAllowed(true);
        }
        product.setReturnPolicy(policy);

        // Images - Mandatory Front (image 0) and Back (image 1) views, min 2, max 6
        List<String> validImageUrls = request.imageUrls() != null
                ? request.imageUrls().stream().filter(u -> u != null && !u.isBlank()).map(String::trim).toList()
                : List.of();

        if (validImageUrls.size() < 2 || validImageUrls.size() > 6) {
            throw new IllegalArgumentException("Products must have at least 2 images (Front and Back views) and at most 6 images.");
        }

        for (int i = 0; i < validImageUrls.size(); i++) {
            String imgUrl = validImageUrls.get(i);
            String viewType = (i == 0) ? "FRONT" : (i == 1) ? "BACK" : "ADDITIONAL";
            ProductImage img = new ProductImage(product, imgUrl, product.getName(), i, i == 0, viewType);
            product.addImage(img);
        }

        // Variants
        if (request.variants() != null && !request.variants().isEmpty()) {
            for (ProductCreateRequest.VariantCreateRequest vReq : request.variants()) {
                pricingService.validatePrice(vReq.price(), vReq.discountedPrice());
                String variantSku = vReq.sku() != null && !vReq.sku().isBlank()
                        ? vReq.sku().trim().toUpperCase()
                        : sku + "-V" + (product.getVariants().size() + 1);

                ProductVariant variant = new ProductVariant(
                        product,
                        variantSku,
                        vReq.variantName(),
                        vReq.price(),
                        vReq.discountedPrice(),
                        vReq.weightGrams()
                );
                product.addVariant(variant);
            }
        }

        Product saved = productRepository.save(product);

        // Initialize Inventory
        int totalStock = 0;
        if (saved.getVariants() != null && !saved.getVariants().isEmpty()) {
            for (int i = 0; i < saved.getVariants().size(); i++) {
                ProductVariant variant = saved.getVariants().get(i);
                int variantStock = (request.variants() != null && request.variants().size() > i && request.variants().get(i).stock() != null)
                        ? request.variants().get(i).stock()
                        : 0;
                inventoryService.initializeStock(saved, variant, variantStock, 5);
                totalStock += variantStock;
            }
        } else {
            int stock = request.initialStock() != null ? request.initialStock() : 0;
            inventoryService.initializeStock(saved, null, stock, 5);
            totalStock = stock;
        }

        if (auditService != null) {
            auditService.recordAudit("Product", saved.getSku(), "PRODUCT_CREATED", "ADMIN", null, saved.getName(), null);
        }

        return ProductDetailDto.fromEntity(saved, totalStock);
    }

    @Transactional
    public ProductDetailDto updateProduct(Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        if (!product.getBrand().getId().equals(request.brandId())) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new IllegalArgumentException("Brand not found with id: " + request.brandId()));
            product.setBrand(brand);
        }

        if (!product.getCategory().getId().equals(request.categoryId())) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + request.categoryId()));
            product.setCategory(category);
        }

        pricingService.validatePrice(request.basePrice(), request.discountedPrice());

        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setDiscountedPrice(request.discountedPrice());
        product.setBarcode(request.barcode());
        product.setPetType(request.petType());
        product.setLifeStage(request.lifeStage());
        product.setProductType(request.productType());
        if (request.isFeatured() != null) {
            product.setIsFeatured(request.isFeatured());
        }

        if (request.returnPolicy() != null && product.getReturnPolicy() != null) {
            ProductCreateRequest.ReturnPolicyCreateRequest reqPol = request.returnPolicy();
            ProductReturnPolicy policy = product.getReturnPolicy();
            if (reqPol.returnAllowed() != null) policy.setReturnAllowed(reqPol.returnAllowed());
            if (reqPol.returnWindowDays() != null) policy.setReturnWindowDays(reqPol.returnWindowDays());
            if (reqPol.originalPackagingRequired() != null) policy.setOriginalPackagingRequired(reqPol.originalPackagingRequired());
            if (reqPol.unusedConditionRequired() != null) policy.setUnusedConditionRequired(reqPol.unusedConditionRequired());
            if (reqPol.refundAllowed() != null) policy.setRefundAllowed(reqPol.refundAllowed());
            if (reqPol.refundMethod() != null) policy.setRefundMethod(reqPol.refundMethod());
            if (reqPol.customConditions() != null) policy.setCustomConditions(reqPol.customConditions());
        }

        Product updated = productRepository.save(product);
        if (auditService != null) {
            auditService.recordAudit("Product", updated.getSku(), "PRODUCT_UPDATED", "ADMIN", null, updated.getName(), null);
        }
        Integer stock = inventoryRepository.findTotalAvailableStockByProductId(updated.getId());
        return ProductDetailDto.fromEntity(updated, stock != null ? stock : 0);
    }

    @Transactional
    public ProductDetailDto toggleProductStatus(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        product.setIsActive(!Boolean.TRUE.equals(product.getIsActive()));
        Product saved = productRepository.save(product);
        if (auditService != null) {
            auditService.recordAudit("Product", saved.getSku(), "PRODUCT_STATUS_TOGGLED", "ADMIN", String.valueOf(!Boolean.TRUE.equals(saved.getIsActive())), String.valueOf(saved.getIsActive()), null);
        }

        Integer stock = inventoryRepository.findTotalAvailableStockByProductId(saved.getId());
        return ProductDetailDto.fromEntity(saved, stock != null ? stock : 0);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getSimilarRecommendations(String query, String categorySlug, String petType, int limit) {
        int max = limit > 0 && limit <= 24 ? limit : 8;
        Pageable pageable = PageRequest.of(0, max);

        String normalizedPetType = null;
        String detectedKeyword = null;
        String detectedBrand = null;

        if (petType != null && !petType.trim().isBlank()) {
            normalizedPetType = petType.trim();
        }

        if (query != null && !query.trim().isBlank()) {
            String lower = query.trim().toLowerCase();

            // Detect Pet Type if not already provided
            if (normalizedPetType == null) {
                if (lower.contains("dog") || lower.contains("puppy") || lower.contains("canine")) {
                    normalizedPetType = "Dog";
                } else if (lower.contains("cat") || lower.contains("kitten") || lower.contains("feline")) {
                    normalizedPetType = "Cat";
                } else if (lower.contains("bird") || lower.contains("parrot") || lower.contains("avian")) {
                    normalizedPetType = "Bird";
                } else if (lower.contains("fish") || lower.contains("aquarium")) {
                    normalizedPetType = "Fish";
                }
            }

            // Detect usage category/keywords
            if (lower.contains("food") || lower.contains("kibble") || lower.contains("gravy") || lower.contains("biscuit") || lower.contains("treat")) {
                detectedKeyword = "food";
            } else if (lower.contains("shampoo") || lower.contains("groom") || lower.contains("soap") || lower.contains("litter") || lower.contains("brush")) {
                detectedKeyword = "grooming";
            } else if (lower.contains("toy") || lower.contains("chew") || lower.contains("ball") || lower.contains("rope")) {
                detectedKeyword = "toy";
            } else if (lower.contains("collar") || lower.contains("leash") || lower.contains("harness") || lower.contains("belt")) {
                detectedKeyword = "collar";
            }

            // Detect brand
            for (String b : List.of("royal canin", "pedigree", "whiskas", "drools", "farmina", "arden grange", "purepet", "meat up", "himalaya", "sheba")) {
                if (lower.contains(b)) {
                    detectedBrand = b;
                    break;
                }
            }
        }

        List<Product> products = productRepository.findSimilarProducts(
                normalizedPetType, categorySlug, detectedBrand, detectedKeyword, pageable
        );

        if (products == null || products.isEmpty()) {
            products = productRepository.findTopActiveProducts(pageable);
        }

        return products.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && !Boolean.TRUE.equals(p.getIsArchived()))
                .map(p -> {
                    Integer stock = inventoryRepository.findTotalAvailableStockByProductId(p.getId());
                    return ProductSummaryDto.fromEntity(p, stock != null && stock > 0);
                })
                .toList();
    }

    @Transactional
    public ProductRemovalResult removeProduct(Long id, String adminUsername) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        boolean wasActive = Boolean.TRUE.equals(product.getIsActive());

        // Universal Product Archival Policy:
        // Products are NEVER permanently deleted through admin product removal.
        // All product removals perform archival to preserve transactional integrity, historical
        // orders, invoices, payments, refunds, return records, inventory ledger entries, and audit compliance.
        product.setIsArchived(true);
        product.setIsActive(false);
        product.setArchivedAt(java.time.Instant.now());
        product.setArchivedBy(adminUsername != null ? adminUsername : "ADMIN");

        // Transactionally purge active shopping carts referencing this archived product
        if (cartItemRepository != null) {
            cartItemRepository.deleteByProductId(id);
        }

        productRepository.save(product);

        if (auditService != null) {
            auditService.recordAudit("Product", product.getSku(), "PRODUCT_ARCHIVED", adminUsername,
                    wasActive ? "ACTIVE" : "INACTIVE", "ARCHIVED", null);
        }

        return new ProductRemovalResult(
                product.getId(),
                product.getName(),
                product.getSku(),
                "ARCHIVED",
                "Product has been safely archived from the customer catalog. All historical orders, invoices, payments, refunds, and reports remain intact."
        );
    }

    @Transactional
    public ProductDetailDto restoreProduct(Long id, String adminUsername, Boolean explicitActivate) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        boolean targetActive;
        if (explicitActivate != null) {
            targetActive = explicitActivate;
        } else {
            // Check audit log for pre-archive active status to preserve pre-archive state safely
            if (auditLogRepository != null) {
                var lastArchiveLog = auditLogRepository
                        .findFirstByEntityNameAndEntityIdAndActionOrderByCreatedAtDesc("Product", product.getSku(), "PRODUCT_ARCHIVED");
                if (lastArchiveLog.isEmpty() && product.getId() != null) {
                    lastArchiveLog = auditLogRepository
                            .findFirstByEntityNameAndEntityIdAndActionOrderByCreatedAtDesc("Product", String.valueOf(product.getId()), "PRODUCT_ARCHIVED");
                }
                // Safe default: only activate if explicitly recorded as "ACTIVE"; null, empty, malformed, or historical pre-audit records safely evaluate to inactive (false)
                targetActive = lastArchiveLog.isPresent()
                        && lastArchiveLog.get().getOldValue() != null
                        && "ACTIVE".equalsIgnoreCase(lastArchiveLog.get().getOldValue().trim());
            } else {
                targetActive = false; // Safe default: remain inactive if audit log repository is unavailable
            }
        }

        product.setIsArchived(false);
        product.setIsActive(targetActive);
        product.setArchivedAt(null);
        product.setArchivedBy(null);

        Product saved = productRepository.save(product);
        if (auditService != null) {
            auditService.recordAudit("Product", saved.getSku(), "PRODUCT_RESTORED", adminUsername,
                    "ARCHIVED", targetActive ? "ACTIVE" : "INACTIVE", null);
        }

        Integer stock = inventoryRepository.findTotalAvailableStockByProductId(saved.getId());
        return ProductDetailDto.fromEntity(saved, stock != null ? stock : 0);
    }

    @Transactional
    public ProductDetailDto restoreProduct(Long id, String adminUsername) {
        return restoreProduct(id, adminUsername, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductDetailDto> searchAdminProducts(String query, String status, Long categoryId, Pageable pageable) {
        Specification<Product> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if ("ACTIVE".equalsIgnoreCase(status)) {
                predicates.add(cb.isTrue(root.get("isActive")));
                predicates.add(cb.or(cb.isNull(root.get("isArchived")), cb.isFalse(root.get("isArchived"))));
            } else if ("INACTIVE".equalsIgnoreCase(status)) {
                predicates.add(cb.isFalse(root.get("isActive")));
                predicates.add(cb.or(cb.isNull(root.get("isArchived")), cb.isFalse(root.get("isArchived"))));
            } else if ("ARCHIVED".equalsIgnoreCase(status)) {
                predicates.add(cb.isTrue(root.get("isArchived")));
            }

            if (query != null && !query.trim().isBlank()) {
                String clean = "%" + query.trim().toLowerCase() + "%";
                jakarta.persistence.criteria.Join<Product, Brand> brandJoin = root.join("brand", jakarta.persistence.criteria.JoinType.LEFT);
                jakarta.persistence.criteria.Join<Product, Category> categoryJoin = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), clean),
                        cb.like(cb.lower(root.get("sku")), clean),
                        cb.like(cb.lower(brandJoin.get("name")), clean),
                        cb.like(cb.lower(categoryJoin.get("name")), clean)
                ));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Product> page = productRepository.findAll(spec, pageable);
        return page.map(p -> {
            Integer stock = inventoryRepository.findTotalAvailableStockByProductId(p.getId());
            return ProductDetailDto.fromEntity(p, stock != null ? stock : 0);
        });
    }
}
