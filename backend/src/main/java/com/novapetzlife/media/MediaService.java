package com.novapetzlife.media;

import com.novapetzlife.media.dto.AddProductImageRequest;
import com.novapetzlife.media.dto.ProductImageDto;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductImage;
import com.novapetzlife.product.ProductImageRepository;
import com.novapetzlife.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudinaryCloudName;

    @Value("${app.media.local-storage-enabled:false}")
    private boolean localStorageEnabled;

    @Value("${app.media.upload-dir:uploads/products}")
    private String uploadDir;

    @Value("${app.media.base-url:}")
    private String mediaBaseUrl;

    public MediaService(
            ProductImageRepository productImageRepository,
            ProductRepository productRepository) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
    }

    public boolean isCloudinaryConfigured() {
        // Genuine Cloudinary integration (API credentials and binary upload client) is not implemented in this phase
        return false;
    }

    public boolean isLocalStorageEnabled() {
        return localStorageEnabled;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public boolean isStorageHealthy() {
        if (!localStorageEnabled) {
            return false;
        }
        try {
            Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return Files.isDirectory(path) && Files.isWritable(path);
        } catch (Exception e) {
            log.error("Media storage health check failed for path {}: {}", uploadDir, e.getMessage());
            return false;
        }
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
        String deletedImageUrl = img.getImageUrl();
        productImageRepository.delete(img);
        productImageRepository.flush();

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

        // Safe physical file cleanup: only remove disk asset if not shared by any other product image
        cleanupLocalFileIfUnused(deletedImageUrl);

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
     * Upload handler: accepts multipart file and returns a hosted Cloudinary/CDN or local file URL.
     * When Cloudinary is configured, delivers cloud storage URL.
     * When local storage is enabled, saves the file to disk and serves it via local media endpoint.
     */
    public String uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be null or empty.");
        }

        // 1. File size limit: 5MB maximum
        long maxSizeBytes = 5 * 1024 * 1024; // 5 MB
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File size exceeds maximum permitted limit of 5MB.");
        }

        // 2. MIME type validation
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType + ". Only JPEG, PNG, WEBP, and GIF images are permitted.");
        }

        // 3. Magic bytes / header inspection
        validateMagicBytes(file);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        String assetId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String storedFilename = assetId + "_" + cleanName;

        if (localStorageEnabled) {
            try {
                Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                Path targetLocation = uploadPath.resolve(storedFilename).normalize();
                if (!targetLocation.startsWith(uploadPath)) {
                    throw new SecurityException("Invalid target filename path traversal detected");
                }
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                log.info("Saved media asset locally to {} (size: {} bytes)", targetLocation, file.getSize());

                if (mediaBaseUrl != null && !mediaBaseUrl.isBlank()) {
                    return mediaBaseUrl.replaceAll("/+$", "") + "/api/v1/media/files/" + storedFilename;
                }

                try {
                    return ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/api/v1/media/files/")
                            .path(storedFilename)
                            .toUriString();
                } catch (Exception e) {
                    return "/api/v1/media/files/" + storedFilename;
                }
            } catch (IOException ex) {
                log.error("Failed to store local media file: {}", originalFilename, ex);
                throw new RuntimeException("Could not store media file locally: " + ex.getMessage(), ex);
            }
        }

        throw new IllegalStateException("Local media storage is disabled and genuine cloud storage (Cloudinary) is unconfigured.");
    }

    private boolean isAllowedContentType(String contentType) {
        String lower = contentType.toLowerCase().trim();
        return lower.equals("image/jpeg") || lower.equals("image/jpg") ||
               lower.equals("image/png") || lower.equals("image/webp") ||
               lower.equals("image/gif");
    }

    private void validateMagicBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = in.read(header);
            if (bytesRead < 4) {
                throw new IllegalArgumentException("Uploaded file header is too short to be a valid image.");
            }

            // JPEG: FF D8 FF
            boolean isJpeg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            // PNG: 89 50 4E 47
            boolean isPng = (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
            // GIF: 47 49 46 38 ('GIF8')
            boolean isGif = header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
            // WEBP: 'RIFF' .... 'WEBP'
            boolean isWebp = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && bytesRead >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';

            if (!isJpeg && !isPng && !isGif && !isWebp) {
                throw new IllegalArgumentException("File content signature does not match permitted image formats (JPEG, PNG, WEBP, GIF).");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to inspect uploaded file content: " + e.getMessage(), e);
        }
    }

    private void cleanupLocalFileIfUnused(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains("/api/v1/media/files/")) {
            return;
        }
        try {
            boolean isShared = productImageRepository.existsByImageUrl(imageUrl);
            if (isShared) {
                log.info("Media file {} is still referenced by other product image(s); preserving physical file.", imageUrl);
                return;
            }

            String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1).trim();
            if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return;
            }

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetFile = uploadPath.resolve(filename).normalize();
            if (targetFile.startsWith(uploadPath) && Files.exists(targetFile)) {
                Files.delete(targetFile);
                log.info("Cleaned up orphaned local media file: {}", targetFile);
            }
        } catch (Exception ex) {
            log.warn("Non-fatal: could not delete local media file for {}: {}", imageUrl, ex.getMessage());
        }
    }
}
