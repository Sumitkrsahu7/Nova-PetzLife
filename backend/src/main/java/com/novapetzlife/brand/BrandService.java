package com.novapetzlife.brand;

import com.novapetzlife.category.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<BrandDto> getAllActiveBrands() {
        return brandRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(BrandDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandDto getBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Brand not found with slug: " + slug));
        return BrandDto.fromEntity(brand);
    }

    @Transactional
    public BrandDto createBrand(BrandCreateRequest request) {
        if (brandRepository.existsByName(request.name().trim())) {
            throw new IllegalArgumentException("A brand already exists with name: " + request.name());
        }

        String slug = request.slug() != null && !request.slug().isBlank()
                ? CategoryService.toSlug(request.slug())
                : CategoryService.toSlug(request.name());

        if (brandRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis() % 10000;
        }

        Brand brand = new Brand(
                request.name().trim(),
                slug,
                request.description(),
                request.logoUrl()
        );
        if (request.isActive() != null) {
            brand.setIsActive(request.isActive());
        }

        Brand saved = brandRepository.save(brand);
        return BrandDto.fromEntity(saved);
    }

    @Transactional
    public BrandDto updateBrand(Long id, BrandCreateRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Brand not found with id: " + id));

        brand.setName(request.name().trim());
        if (request.description() != null) {
            brand.setDescription(request.description());
        }
        if (request.logoUrl() != null) {
            brand.setLogoUrl(request.logoUrl());
        }
        if (request.isActive() != null) {
            brand.setIsActive(request.isActive());
        }

        Brand updated = brandRepository.save(brand);
        return BrandDto.fromEntity(updated);
    }

    @Transactional
    public void toggleActiveStatus(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Brand not found with id: " + id));
        brand.setIsActive(!Boolean.TRUE.equals(brand.getIsActive()));
        brandRepository.save(brand);
    }
}
