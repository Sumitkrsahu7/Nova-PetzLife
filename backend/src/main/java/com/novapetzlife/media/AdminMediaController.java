package com.novapetzlife.media;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.media.dto.AddProductImageRequest;
import com.novapetzlife.media.dto.ProductImageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'STORE_MANAGER')")
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

    @DeleteMapping("/products/{productId}/images/{imageId}")
    @Operation(summary = "Delete product image", description = "Removes an image from the product gallery")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        mediaService.deleteProductImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image deleted successfully", null));
    }

    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image file", description = "Uploads an image file to CDN/Cloudinary pipeline")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(@RequestParam("file") MultipartFile file) {
        String url = mediaService.uploadMedia(file);
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded successfully", Map.of("url", url)));
    }
}
