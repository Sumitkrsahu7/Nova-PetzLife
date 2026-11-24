package com.novapetzlife.media;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.email.BrevoEmailService;
import com.novapetzlife.email.ResendEmailService;
import com.novapetzlife.media.dto.AddProductImageRequest;
import com.novapetzlife.media.dto.ProductImageDto;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductImage;
import com.novapetzlife.product.ProductImageRepository;
import com.novapetzlife.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MediaStorageAndLoggingSecurityTest {

    @Autowired
    private BrevoEmailService brevoEmailService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @TempDir
    Path tempUploadDir;

    private Product testProduct;
    private Path originalUploadDir;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Brand brand = brandRepository.save(new Brand("Brand-" + suffix, "brand-" + suffix, "Description"));
        Category category = categoryRepository.save(new Category("Cat-" + suffix, "cat-" + suffix, "Description", null, 0));

        testProduct = new Product(
                brand, category, "Test Pet Care " + suffix, "slug-" + suffix, "SKU-" + suffix,
                new BigDecimal("499.00"), new BigDecimal("499.00")
        );
        testProduct = productRepository.save(testProduct);
    }

    @AfterEach
    void tearDown() {
        // Reset MediaService reflection configuration
        ReflectionTestUtils.setField(mediaService, "localStorageEnabled", false);
        ReflectionTestUtils.setField(mediaService, "uploadDir", "uploads/products");
        ReflectionTestUtils.setField(mediaService, "cloudinaryCloudName", "");
    }

    @Test
    @DisplayName("NPZ-SEC-LOG-01: BrevoEmailService deferred delivery never leaks reset token, full URL, or raw email")
    void testBrevoEmailService_DeferredDelivery_DoesNotLogSensitiveData() {
        Logger logger = (Logger) LoggerFactory.getLogger(BrevoEmailService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String rawToken = "SECRET_TOKEN_XYZ_9876543210_UNSAFE";
        String recipient = "aditya.testuser@example.com";

        try {
            brevoEmailService.sendPasswordResetEmail(recipient, "Aditya Test", rawToken);

            List<ILoggingEvent> logList = listAppender.list;
            assertFalse(logList.isEmpty(), "At least one log event must be generated");

            for (ILoggingEvent event : logList) {
                String message = event.getFormattedMessage();
                // 1. Raw token must NEVER appear in logs
                assertFalse(message.contains(rawToken), "Raw password reset token must never appear in logs");
                // 2. Complete reset URL must NEVER appear in logs
                assertFalse(message.contains("reset-password?token="), "Password reset action URL must never appear in logs");
                // 3. Full unmasked recipient email must NEVER appear in deferred logs
                assertFalse(message.contains("aditya.testuser@example.com"), "Full recipient email must not appear unmasked");
                // 4. Safe masked representation must be used
                assertTrue(message.contains("ad***@example.com"), "Masked recipient email should be present");
                // 5. Safe operational status must be recorded
                assertTrue(message.contains("[EMAIL DEFERRED]"), "Deferred delivery status should be recorded");
            }
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("NPZ-SEC-LOG-02: ResendEmailService unconfigured delivery never leaks reset token, full URL, or raw email")
    void testResendEmailService_Unconfigured_DoesNotLogSensitiveData() {
        Logger logger = (Logger) LoggerFactory.getLogger(ResendEmailService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String rawToken = "SECRET_TOKEN_RESEND_ABC_123456";
        String recipient = "kavita.customer@example.com";

        try {
            resendEmailService.sendPasswordResetEmail(recipient, "Kavita Customer", rawToken);

            List<ILoggingEvent> logList = listAppender.list;
            assertFalse(logList.isEmpty(), "At least one log event must be recorded");

            for (ILoggingEvent event : logList) {
                String message = event.getFormattedMessage();
                assertFalse(message.contains(rawToken), "Reset token must never be logged");
                assertFalse(message.contains("reset-password?token="), "Reset URL must never be logged");
                assertFalse(message.contains("kavita.customer@example.com"), "Full email must not appear unmasked");
                assertTrue(message.contains("ka***@example.com"), "Masked email must be present");
                assertTrue(message.contains("[EMAIL DEFERRED]"), "Safe deferred status must be logged");
            }
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("NPZ-MEDIA-01: When Cloudinary name is populated, uploadMedia never fabricates Cloudinary URL and uses local storage")
    void testCloudinaryCloudNamePopulated_NeverReturnsFabricatedCloudinaryUrl() throws IOException {
        ReflectionTestUtils.setField(mediaService, "cloudinaryCloudName", "my-demo-cloud");
        ReflectionTestUtils.setField(mediaService, "localStorageEnabled", true);
        ReflectionTestUtils.setField(mediaService, "uploadDir", tempUploadDir.toString());

        // Genuine PNG bytes
        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        MockMultipartFile file = new MockMultipartFile("file", "cat_food.png", "image/png", validPng);

        String resultUrl = mediaService.uploadMedia(file);
        assertNotNull(resultUrl);

        // Crucial safety invariant: Must NOT return fabricated Cloudinary URL
        assertFalse(resultUrl.contains("res.cloudinary.com"), "Must not synthesize fake Cloudinary URL");
        assertTrue(resultUrl.contains("/api/v1/media/files/"), "Must return genuine local media retrieval URL");

        // Verify the file was actually written to the local disk directory
        String filename = resultUrl.substring(resultUrl.lastIndexOf('/') + 1);
        Path savedFile = tempUploadDir.resolve(filename);
        assertTrue(Files.exists(savedFile), "Uploaded file must physically exist in the local storage directory");
        assertEquals(validPng.length, Files.size(savedFile));
    }

    @Test
    @DisplayName("NPZ-MEDIA-02: When local storage is disabled, uploadMedia throws IllegalStateException rather than fabricating URLs")
    void testLocalStorageDisabled_ThrowsStorageUnavailable() {
        ReflectionTestUtils.setField(mediaService, "cloudinaryCloudName", "my-demo-cloud");
        ReflectionTestUtils.setField(mediaService, "localStorageEnabled", false);

        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        MockMultipartFile file = new MockMultipartFile("file", "cat_food.png", "image/png", validPng);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> mediaService.uploadMedia(file));
        assertTrue(ex.getMessage().contains("Local media storage is disabled and genuine cloud storage (Cloudinary) is unconfigured"));
        assertFalse(mediaService.isCloudinaryConfigured(), "isCloudinaryConfigured must return false in this phase");
    }

    @Test
    @DisplayName("NPZ-MEDIA-03: Media deletion safely removes orphaned disk files when not shared by other images")
    void testMediaDeletion_SafelyRemovesOrphanedDiskFile() throws IOException {
        ReflectionTestUtils.setField(mediaService, "localStorageEnabled", true);
        ReflectionTestUtils.setField(mediaService, "uploadDir", tempUploadDir.toString());

        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        MockMultipartFile file1 = new MockMultipartFile("file", "front.png", "image/png", validPng);
        MockMultipartFile file2 = new MockMultipartFile("file", "back.png", "image/png", validPng);
        MockMultipartFile file3 = new MockMultipartFile("file", "side.png", "image/png", validPng);

        String url1 = mediaService.uploadMedia(file1);
        String url2 = mediaService.uploadMedia(file2);
        String url3 = mediaService.uploadMedia(file3);

        ProductImageDto img1 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(url1, "Front view", 0, true));
        ProductImageDto img2 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(url2, "Back view", 1, false));
        ProductImageDto img3 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(url3, "Side view", 2, false));

        String filename3 = url3.substring(url3.lastIndexOf('/') + 1);
        Path physicalFile3 = tempUploadDir.resolve(filename3);
        assertTrue(Files.exists(physicalFile3), "File 3 must exist prior to deletion");

        // Delete image 3 (product retains 2 images: Front and Back)
        mediaService.deleteProductImage(testProduct.getId(), img3.id());

        // Entity is deleted
        assertFalse(productImageRepository.existsById(img3.id()));
        // Physical file is safely removed because no other image uses url3
        assertFalse(Files.exists(physicalFile3), "Orphaned local disk file must be deleted upon image removal");
    }

    @Test
    @DisplayName("NPZ-MEDIA-04: Media deletion preserves disk file when shared by another product image")
    void testMediaDeletion_PreservesDiskFileWhenShared() throws IOException {
        ReflectionTestUtils.setField(mediaService, "localStorageEnabled", true);
        ReflectionTestUtils.setField(mediaService, "uploadDir", tempUploadDir.toString());

        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        MockMultipartFile sharedFile = new MockMultipartFile("file", "shared.png", "image/png", validPng);
        String sharedUrl = mediaService.uploadMedia(sharedFile);

        // Add 3 images to testProduct, where image 2 and image 3 share the same URL
        ProductImageDto img1 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(sharedUrl + "_diff1", "Front", 0, true));
        ProductImageDto img2 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(sharedUrl, "Back", 1, false));
        ProductImageDto img3 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest(sharedUrl, "Side", 2, false));

        String filename = sharedUrl.substring(sharedUrl.lastIndexOf('/') + 1);
        Path physicalFile = tempUploadDir.resolve(filename);
        assertTrue(Files.exists(physicalFile), "Shared file must exist");

        // Delete img3
        mediaService.deleteProductImage(testProduct.getId(), img3.id());

        // Because img2 still references sharedUrl, the physical file must NOT be deleted
        assertTrue(Files.exists(physicalFile), "Shared physical file must be preserved when referenced by img2");
    }

    @Test
    @DisplayName("NPZ-MEDIA-05: Product gallery minimum 2 images requirement and view type maintenance")
    void testProductGallery_MaintainsMinimumTwoImages() {
        ProductImageDto img1 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest("/api/v1/media/files/img1.png", "Front", 0, true));
        ProductImageDto img2 = mediaService.addProductImage(testProduct.getId(), new AddProductImageRequest("/api/v1/media/files/img2.png", "Back", 1, false));

        // Attempting to delete when only 2 images exist must be rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                mediaService.deleteProductImage(testProduct.getId(), img2.id())
        );
        assertTrue(ex.getMessage().contains("Products must have at least 2 images"));

        List<ProductImageDto> gallery = mediaService.getProductImages(testProduct.getId());
        assertEquals(2, gallery.size());
        assertEquals("FRONT", gallery.get(0).viewType());
        assertEquals("BACK", gallery.get(1).viewType());
    }
}
