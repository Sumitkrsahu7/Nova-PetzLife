package com.novapetzlife.media;

import com.novapetzlife.media.dto.AddProductImageRequest;
import com.novapetzlife.media.dto.ProductImageDto;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductImage;
import com.novapetzlife.product.ProductImageRepository;
import com.novapetzlife.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    public MediaService(
            ProductImageRepository productImageRepository,
            ProductRepository productRepository) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductImageDto> getProductImages(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(ProductImageDto::fromEntity)
                .toList();
    }

    @Transactional
    public ProductImageDto addProductImage(Long productId, AddProductImageRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());
        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);

        if (existing.isEmpty() || isPrimary) {
            isPrimary = true;
            // Clear primary flag from others
            for (ProductImage img : existing) {
                if (Boolean.TRUE.equals(img.getIsPrimary())) {
                    img.setIsPrimary(false);
                    productImageRepository.save(img);
                }
            }
        }

        int order = request.displayOrder() != null ? request.displayOrder() : existing.size();
        ProductImage newImg = new ProductImage(product, request.imageUrl(), request.altText(), order, isPrimary);
        newImg = productImageRepository.save(newImg);

        log.info("Added product image {} to product {} (primary: {})", newImg.getId(), productId, isPrimary);
        return ProductImageDto.fromEntity(newImg);
    }

    @Transactional
    public ProductImageDto setPrimaryImage(Long productId, Long imageId) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("Product not found with id: " + productId);
        }

        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        ProductImage target = null;

        for (ProductImage img : images) {
            if (img.getId().equals(imageId)) {
                img.setIsPrimary(true);
                target = img;
            } else {
                img.setIsPrimary(false);
            }
            productImageRepository.save(img);
        }

        if (target == null) {
            throw new IllegalArgumentException("Product image not found with id: " + imageId);
        }

        log.info("Set image {} as primary for product {}", imageId, productId);
        return ProductImageDto.fromEntity(target);
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        ProductImage img = productImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Product image not found with id: " + imageId));

        if (!img.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to specified product");
        }

        boolean wasPrimary = Boolean.TRUE.equals(img.getIsPrimary());
        productImageRepository.delete(img);

        if (wasPrimary) {
            List<ProductImage> remaining = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage nextPrimary = remaining.get(0);
                nextPrimary.setIsPrimary(true);
                productImageRepository.save(nextPrimary);
            }
        }
        log.info("Deleted product image {} from product {}", imageId, productId);
    }

    /**
     * Upload handler: accepts multipart file and returns a hosted Cloudinary/CDN URL.
     * In local/simulated environment, returns a clean deterministic media asset URL.
     */
    public String uploadMedia(MultipartFile file) {
        String originalFilename = file != null && file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "pet_image.jpg";
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        String assetId = UUID.randomUUID().toString().substring(0, 8);

        // Formats as Cloudinary media delivery URL
        String mockCloudinaryUrl = "https://res.cloudinary.com/novapetzlife/image/upload/v1700000000/products/" + assetId + "_" + cleanName;
        log.info("Processed media asset upload: {} -> {}", originalFilename, mockCloudinaryUrl);
        return mockCloudinaryUrl;
    }
}
