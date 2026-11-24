package com.novapetzlife.media;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.media.dto.AddProductImageRequest;
import com.novapetzlife.media.dto.ProductImageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Media & Product Images", description = "Management of product gallery images and media asset uploads")
public class AdminMediaController {

    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/products/{productId}/images")
    @Operation(summary = "List product images", description = "Retrieves all gallery images for a product")
    public ResponseEntity<ApiResponse<List<ProductImageDto>>> getProductImages(@PathVariable Long productId) {
        List<ProductImageDto> images = mediaService.getProductImages(productId);
        return ResponseEntity.ok(ApiResponse.ok(images));
    }

    @PostMapping("/products/{productId}/images")
    @Operation(summary = "Add image to product gallery", description = "Adds a new image with alt text and display order")
    public ResponseEntity<ApiResponse<ProductImageDto>> addProductImage(
            @PathVariable Long productId,
            @Valid @RequestBody AddProductImageRequest request) {
        ProductImageDto image = mediaService.addProductImage(productId, request);
        return ResponseEntity.ok(ApiResponse.ok("Product image added successfully", image));
    }

    @PutMapping("/products/{productId}/images/{imageId}/primary")
    @Operation(summary = "Set primary product image", description = "Designates an image as the primary cover photo for the product")
    public ResponseEntity<ApiResponse<ProductImageDto>> setPrimaryImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        ProductImageDto image = mediaService.setPrimaryImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.ok("Primary image updated", image));
    }

    @PutMapping("/products/{productId}/images/{imageId}/replace")
    @Operation(summary = "Replace product image", description = "Replaces the URL and alt text of an existing product image")
    public ResponseEntity<ApiResponse<ProductImageDto>> replaceProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestBody Map<String, String> body) {
        String newUrl = body.get("imageUrl");
        String newAlt = body.get("altText");
        ProductImageDto image = mediaService.replaceProductImage(productId, imageId, newUrl, newAlt);
        return ResponseEntity.ok(ApiResponse.ok("Product image replaced successfully", image));
    }

    @PutMapping("/products/{productId}/images/reorder")
    @Operation(summary = "Reorder product images", description = "Updates the display order and view types (Front/Back/Additional) for all images of a product")
    public ResponseEntity<ApiResponse<List<ProductImageDto>>> reorderProductImages(
            @PathVariable Long productId,
            @RequestBody List<Long> imageIdsInOrder) {
        List<ProductImageDto> images = mediaService.reorderProductImages(productId, imageIdsInOrder);
        return ResponseEntity.ok(ApiResponse.ok("Product images reordered successfully", images));
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    @Operation(summary = "Delete product image", description = "Removes an image from the product gallery")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        mediaService.deleteProductImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image deleted successfully", null));
    }

    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image file", description = "Validates and processes image uploads with strict magic-byte verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be null or empty.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds 5MB limit.");
        }

        // 1. Filename & Path Traversal Validation
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : "";
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename: Path traversal characters are not permitted.");
        }

        String lowerName = originalFilename.toLowerCase();
        boolean hasValidExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".webp");
        if (!hasValidExt) {
            throw new IllegalArgumentException("Invalid file extension. Only .jpg, .jpeg, .png, and .webp extensions are supported.");
        }

        // 2. MIME Header Validation
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/webp"))) {
            throw new IllegalArgumentException("Invalid file MIME format. Only image/jpeg, image/png, and image/webp are supported.");
        }

        // 3. True Content Inspection via Magic Bytes
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file contents", e);
        }

        if (bytes.length < 12) {
            throw new IllegalArgumentException("Malformed image file: Header too small to be a valid image.");
        }

        boolean isJpeg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        boolean isPng = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
        boolean isWebp = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';

        if (!isJpeg && !isPng && !isWebp) {
            throw new IllegalArgumentException("Invalid image content. File signature does not match genuine JPEG, PNG, or WebP image data.");
        }

        // 4. Content Scanning against Disguised Executable, HTML, XML, or SVG Payloads
        int scanLength = Math.min(bytes.length, 1024);
        String headerSample = new String(bytes, 0, scanLength, StandardCharsets.US_ASCII).toLowerCase();
        if (headerSample.contains("<?xml") || headerSample.contains("<svg") || headerSample.contains("<html")
                || headerSample.contains("<!doctype") || headerSample.contains("<script")) {
            throw new IllegalArgumentException("Malicious content detected: HTML, XML, SVG, and script payloads are strictly rejected.");
        }

        // 5. Storage Provider Reality: Check whether local storage is enabled
        if (!mediaService.isLocalStorageEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(
                            "Cloudinary CDN media storage is unconfigured (INTEGRATION_STUB) and local media storage is disabled. Remote image upload is unavailable until cloud storage credentials are configured or local storage is enabled.",
                            List.of(
                                    "status: STORAGE_UNCONFIGURED_STUB",
                                    "provider: Cloudinary",
                                    "localStorageEnabled: false",
                                    "supportedFormats: JPEG, PNG, WebP"
                            )
                    ));
        }

        String url = mediaService.uploadMedia(file);
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded successfully", Map.of("url", url)));
    }
}
