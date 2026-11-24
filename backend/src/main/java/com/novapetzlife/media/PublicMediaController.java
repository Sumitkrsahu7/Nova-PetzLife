package com.novapetzlife.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Public Media", description = "Public media asset retrieval for product images")
public class PublicMediaController {

    private static final Logger log = LoggerFactory.getLogger(PublicMediaController.class);

    private final MediaService mediaService;

    public PublicMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/files/{filename:.+}")
    @Operation(summary = "Get media file", description = "Retrieves locally stored media files by filename")
    public ResponseEntity<Resource> getMediaFile(@PathVariable String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path baseDir = Paths.get(mediaService.getUploadDir()).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(filename).normalize();

            // Strict path traversal defense
            if (!filePath.startsWith(baseDir)) {
                log.warn("Path traversal detected in media request: {}", filename);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // Determine content type safely
            String contentType = null;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException ignored) {}

            if (contentType == null) {
                String lower = filename.toLowerCase();
                if (lower.endsWith(".png")) contentType = "image/png";
                else if (lower.endsWith(".webp")) contentType = "image/webp";
                else contentType = "image/jpeg";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Malformed URL for media file: {}", filename, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
