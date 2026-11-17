package com.novapetzlife.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.cart.CartItemRepository;
import com.novapetzlife.cart.CartRepository;
import com.novapetzlife.inventory.InventoryRepository;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.inventory.InventoryTransactionRepository;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Brand royalCanin;
    private Brand whiskas;
    private Brand pedigree;

    private Category catCategory;
    private Category catDryFoodSubCategory;
    private Category dogCategory;
    private Category dogFoodSubCategory;

    private Product royalCaninProduct;
    private Product whiskasProduct;
    private Product pedigreeProduct;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // Brands
        royalCanin = brandRepository.save(new Brand("Royal Canin", "royal-canin-" + suffix, "Premium veterinary pet nutrition"));
        whiskas = brandRepository.save(new Brand("Whiskas", "whiskas-" + suffix, "Nutritious cat food and gravies"));
        pedigree = brandRepository.save(new Brand("Pedigree", "pedigree-" + suffix, "Wholesome dog food and nutrition"));

        // Categories & Hierarchies
        catCategory = categoryRepository.save(new Category("Cat Food", "cat-food-" + suffix, "All cat food products", null, 1));
        catDryFoodSubCategory = categoryRepository.save(new Category("Dry Cat Food", "dry-cat-food-" + suffix, "Crunchy kibble for cats", catCategory, 1));

        dogCategory = categoryRepository.save(new Category("Dog Food", "dog-food-" + suffix, "All dog food products", null, 2));
        dogFoodSubCategory = categoryRepository.save(new Category("Dry Dog Food", "dry-dog-food-" + suffix, "Crunchy kibble for dogs", dogCategory, 1));

        // Product 1: Royal Canin (Dry Cat Food) - In Stock
        royalCaninProduct = new Product(
                royalCanin,
                catDryFoodSubCategory,
                "Royal Canin Fit 32 Adult Cat Kibble",
                "royal-canin-fit-32-" + suffix,
                "RC-FIT32-" + suffix.toUpperCase(),
                new BigDecimal("850.00"),
                new BigDecimal("799.00")
        );
        royalCaninProduct.setPetType("Cat");
        royalCaninProduct.setLifeStage("Adult");
        royalCaninProduct.setIsFeatured(true);
        royalCaninProduct = productRepository.save(royalCaninProduct);
        inventoryService.initializeStock(royalCaninProduct, null, 25, 5);

        // Product 2: Whiskas (Cat Food Parent) - Out Of Stock
        whiskasProduct = new Product(
                whiskas,
                catCategory,
                "Whiskas Ocean Fish Kitten Gravy",
                "whiskas-ocean-fish-" + suffix,
                "WH-FISH-" + suffix.toUpperCase(),
                new BigDecimal("450.00"),
                new BigDecimal("399.00")
        );
        whiskasProduct.setPetType("Cat");
        whiskasProduct.setLifeStage("Kitten");
        whiskasProduct.setIsFeatured(false);
        whiskasProduct = productRepository.save(whiskasProduct);
        inventoryService.initializeStock(whiskasProduct, null, 0, 5);

        // Product 3: Pedigree (Dog Food) - In Stock
        pedigreeProduct = new Product(
                pedigree,
                dogFoodSubCategory,
                "Pedigree Adult Chicken & Vegetables",
                "pedigree-adult-chicken-" + suffix,
                "PD-CHK-" + suffix.toUpperCase(),
                new BigDecimal("1200.00"),
                new BigDecimal("1099.00")
        );
        pedigreeProduct.setPetType("Dog");
        pedigreeProduct.setLifeStage("Adult");
        pedigreeProduct.setIsFeatured(true);
        pedigreeProduct = productRepository.save(pedigreeProduct);
        inventoryService.initializeStock(pedigreeProduct, null, 15, 5);
    }

    @Test
    @DisplayName("Category-Aware Search: Searching 'Cat' matches only cat products, dog products are strictly segregated")
    void testCategoryAndPetTypeSegregation() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("petType", "Cat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].petType", everyItem(equalToIgnoringCase("Cat"))));
    }

    @Test
    @DisplayName("Category Hierarchy: Filtering by parent 'cat-food' matches subcategory 'dry-cat-food' products")
    void testCategoryHierarchyInheritance() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("categorySlug", catCategory.getSlug())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].name", hasItem(containsString("Royal Canin Fit 32"))))
                .andExpect(jsonPath("$.data.content[*].name", hasItem(containsString("Whiskas Ocean Fish"))));
    }

    @Test
    @DisplayName("Brand Isolation: Filtering by brand 'royal-canin' isolates Royal Canin from Whiskas and Pedigree")
    void testBrandIsolation() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("brandSlug", royalCanin.getSlug())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].brandName").value("Royal Canin"))
                .andExpect(jsonPath("$.data.content[0].name", containsString("Royal Canin Fit 32")));
    }

    @Test
    @DisplayName("In-Stock Filter: Excluding out-of-stock products returns only items with stock > 0")
    void testInStockFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("inStockOnly", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].inStock", everyItem(is(true))))
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem(containsString("Whiskas Ocean Fish")))));
    }

    @Test
    @DisplayName("Price Range Filter: Filtering between ₹700 and ₹900 returns only Royal Canin (₹799)")
    void testPriceRangeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "700.00")
                        .param("maxPrice", "900.00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].name", containsString("Royal Canin Fit 32")));
    }

    @Test
    @DisplayName("Product Details: Retrieving product by slug returns detailed pricing, brand and inventory")
    void testGetProductBySlug() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + royalCaninProduct.getSlug())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(royalCaninProduct.getSlug()))
                .andExpect(jsonPath("$.data.brandName").value("Royal Canin"))
                .andExpect(jsonPath("$.data.availableStock").value(25))
                .andExpect(jsonPath("$.data.discountedPrice").value(799.00));
    }

    @Test
    @DisplayName("Featured Products: Endpoint returns only featured active products")
    void testFeaturedProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products/featured")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].isFeatured", everyItem(is(true))));
    }
}
