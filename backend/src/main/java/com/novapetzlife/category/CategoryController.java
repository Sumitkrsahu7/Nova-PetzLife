package com.novapetzlife.category;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Categories", description = "Hierarchical pet category management and navigation tree")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories/tree")
    @Operation(summary = "Get full active category hierarchy tree for customer navigation")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategoryTree() {
        List<CategoryDto> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @GetMapping("/categories/flat")
    @Operation(summary = "Get flat list of active categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getAllActiveCategories() {
        List<CategoryDto> categories = categoryService.getAllActiveFlat();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @GetMapping("/categories/{slug}")
    @Operation(summary = "Get category details by slug")
    public ResponseEntity<ApiResponse<CategoryDto>> getCategoryBySlug(@PathVariable String slug) {
        CategoryDto category = categoryService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.ok(category));
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_CREATE', 'PRODUCT_EDIT')")
    @Operation(summary = "Create category or subcategory (Admin only)")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryDto created = categoryService.createCategory(request);
        return ResponseEntity.ok(ApiResponse.ok("Category created successfully", created));
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_EDIT')")
    @Operation(summary = "Update category (Admin only)")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryDto updated = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Category updated successfully", updated));
    }

    @RequestMapping(value = {"/admin/categories/{id}/status", "/admin/categories/{id}/toggle-status"}, method = {RequestMethod.PATCH, RequestMethod.DELETE})
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'PRODUCT_EDIT')")
    @Operation(summary = "Toggle category active state (Admin only)")
    public ResponseEntity<ApiResponse<Void>> toggleCategoryStatus(@PathVariable Long id) {
        categoryService.toggleActiveStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Category status toggled successfully", null));
    }
}
