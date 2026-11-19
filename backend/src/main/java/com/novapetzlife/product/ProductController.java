package com.novapetzlife.product;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@Tag(name = "Product Catalog", description = "Product discovery, category-aware search, brand isolation, and inventory status")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/v1/products")
    @Operation(summary = "Search and filter products", description = "Search catalog with strict category hierarchy, brand isolation, pet type, life stage, and price ranges")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDto>>> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String brandSlug,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String lifeStage,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProp));

        Page<ProductSummaryDto> result = productService.searchProducts(
                query, categorySlug, brandSlug, petType, lifeStage, minPrice, maxPrice, inStockOnly, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/api/v1/products/featured")
    @Operation(summary = "Get featured products", description = "Retrieve featured active products for the homepage carousel and showcases")
    public ResponseEntity<ApiResponse<List<ProductSummaryDto>>> getFeaturedProducts() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getFeaturedProducts()));
    }

    @GetMapping("/api/v1/products/suggestions")
    @Operation(summary = "Get product search autocomplete suggestions", description = "Returns active matching products by name, brand, or category")
    public ResponseEntity<ApiResponse<List<ProductSuggestionDto>>> getSuggestions(@RequestParam(required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getSearchSuggestions(query)));
    }

    @GetMapping("/api/v1/products/{idOrSlug}")
    @Operation(summary = "Get product details by slug or ID", description = "Retrieve full product details including variants, images, and owner return policy snapshot")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProduct(@PathVariable String idOrSlug) {
        try {
            Long id = Long.parseLong(idOrSlug);
            return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(ApiResponse.ok(productService.getProductBySlug(idOrSlug)));
        }
    }

    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_CREATE')")
    @Operation(summary = "Create product (Admin)", description = "Add a new product with brand, category, variants, images, return policy, and initial inventory")
    public ResponseEntity<ApiResponse<ProductDetailDto>> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductDetailDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Product created successfully", created));
    }

    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_EDIT')")
    @Operation(summary = "Update product (Admin)", description = "Update product details, brand, category, pricing, and return policy")
    public ResponseEntity<ApiResponse<ProductDetailDto>> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductCreateRequest request) {
        ProductDetailDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Product updated successfully", updated));
    }

    @RequestMapping(value = {"/api/v1/admin/products/{id}/status", "/api/v1/admin/products/{id}/toggle-status"}, method = {RequestMethod.PATCH, RequestMethod.DELETE})
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_EDIT')")
    @Operation(summary = "Toggle active status (Admin)", description = "Activate or deactivate a product from customer catalog view")
    public ResponseEntity<ApiResponse<ProductDetailDto>> toggleProductStatus(@PathVariable Long id) {
        ProductDetailDto updated = productService.toggleProductStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Product status toggled successfully", updated));
    }
}
