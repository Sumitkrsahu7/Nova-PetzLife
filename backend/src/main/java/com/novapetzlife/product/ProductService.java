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

    public ProductService(
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            PricingService pricingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.audit.AuditService auditService) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.auditService = auditService;
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
                .orElseThrow(() -> new IllegalArgumentException("Product not found with slug: " + slug));

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
}
