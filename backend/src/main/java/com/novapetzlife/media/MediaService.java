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

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        if (existing.size() >= 6) {
            throw new IllegalArgumentException("Maximum of 6 images allowed per product.");
        }

        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());

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
        String viewType = existing.isEmpty() ? "FRONT" : (existing.size() == 1 ? "BACK" : "ADDITIONAL");
        ProductImage newImg = new ProductImage(product, request.imageUrl(), request.altText(), order, isPrimary, viewType);
        newImg = productImageRepository.save(newImg);

        log.info("Added product image {} to product {} (primary: {}, viewType: {})", newImg.getId(), productId, isPrimary, viewType);
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
                img.setViewType("FRONT");
                target = img;
            } else {
                img.setIsPrimary(false);
                if ("FRONT".equalsIgnoreCase(img.getViewType())) {
                    img.setViewType("ADDITIONAL");
                }
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

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        if (existing.size() <= 2) {
            throw new IllegalArgumentException("Cannot delete image. Products must have at least 2 images (Front and Back views).");
        }

        boolean wasPrimary = Boolean.TRUE.equals(img.getIsPrimary());
        productImageRepository.delete(img);

        List<ProductImage> remaining = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (int i = 0; i < remaining.size(); i++) {
            ProductImage rem = remaining.get(i);
            rem.setDisplayOrder(i);
            if (i == 0) {
                rem.setIsPrimary(true);
                rem.setViewType("FRONT");
            } else if (i == 1) {
                rem.setIsPrimary(false);
                rem.setViewType("BACK");
            } else {
                rem.setIsPrimary(false);
                rem.setViewType("ADDITIONAL");
            }
            productImageRepository.save(rem);
        }

        log.info("Deleted product image {} from product {}", imageId, productId);
    }

    @Transactional
    public ProductImageDto replaceProductImage(Long productId, Long imageId, String newImageUrl, String newAltText) {
        ProductImage img = productImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Product image not found with id: " + imageId));

        if (!img.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to specified product");
        }

        if (newImageUrl != null && !newImageUrl.isBlank()) {
            img.setImageUrl(newImageUrl.trim());
        }
        if (newAltText != null) {
            img.setAltText(newAltText.trim());
        }
        img = productImageRepository.save(img);
        log.info("Replaced product image {} for product {}", imageId, productId);
        return ProductImageDto.fromEntity(img);
    }

    @Transactional
    public List<ProductImageDto> reorderProductImages(Long productId, List<Long> imageIdsInOrder) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("Product not found with id: " + productId);
        }

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        if (imageIdsInOrder == null || imageIdsInOrder.size() != existing.size()) {
            throw new IllegalArgumentException("All " + existing.size() + " product images must be included in the reorder request");
        }

        for (int i = 0; i < imageIdsInOrder.size(); i++) {
            Long imgId = imageIdsInOrder.get(i);
            ProductImage img = existing.stream().filter(e -> e.getId().equals(imgId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Image ID " + imgId + " does not belong to product " + productId));
            img.setDisplayOrder(i);
            if (i == 0) {
                img.setIsPrimary(true);
                img.setViewType("FRONT");
            } else if (i == 1) {
                img.setIsPrimary(false);
                img.setViewType("BACK");
            } else {
                img.setIsPrimary(false);
                img.setViewType("ADDITIONAL");
            }
            productImageRepository.save(img);
        }

        log.info("Reordered images for product {}: {}", productId, imageIdsInOrder);
        return getProductImages(productId);
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
