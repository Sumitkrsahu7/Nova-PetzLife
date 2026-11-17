package com.novapetzlife.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CategoryService {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategoryTree() {
        return categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllActiveFlat() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with slug: " + slug));
        return CategoryDto.fromEntity(category);
    }

    @Transactional
    public CategoryDto createCategory(CategoryCreateRequest request) {
        String slug = request.slug() != null && !request.slug().isBlank()
                ? toSlug(request.slug())
                : toSlug(request.name());

        if (categoryRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis() % 10000;
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found with id: " + request.parentId()));
        }

        Category category = new Category(
                request.name().trim(),
                slug,
                request.description(),
                parent,
                request.displayOrder() != null ? request.displayOrder() : 0
        );
        category.setImageUrl(request.imageUrl());

        Category saved = categoryRepository.save(category);
        return CategoryDto.fromEntity(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryCreateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));

        category.setName(request.name().trim());
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.imageUrl() != null) {
            category.setImageUrl(request.imageUrl());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + request.parentId()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category updated = categoryRepository.save(category);
        return CategoryDto.fromEntity(updated);
    }

    @Transactional
    public void toggleActiveStatus(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categoryRepository.save(category);
    }

    public static String toSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
