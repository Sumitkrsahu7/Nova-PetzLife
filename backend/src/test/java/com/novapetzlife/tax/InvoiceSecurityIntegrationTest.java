package com.novapetzlife.tax;

import com.novapetzlife.brand.Brand;
import com.novapetzlife.brand.BrandRepository;
import com.novapetzlife.category.Category;
import com.novapetzlife.category.CategoryRepository;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.order.*;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InvoiceSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService invoicePdfService;

    private User userA;
    private User userB;
    private Customer customerA;
    private Customer customerB;
    private Order orderCustomerA;
    private Order orderCustomerB;
    private OrderItem itemWithoutHsn;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Customer account")));

        String uuidA = UUID.randomUUID().toString().substring(0, 6);
        userA = new User("customer_a_" + uuidA + "@test.com", passwordEncoder.encode("Password123!"), "Alice", "Customer", "98471" + uuidA);
        userA.setRoles(Set.of(customerRole));
        userA = userRepository.save(userA);

        customerA = new Customer(userA, userA.getEmail(), userA.getPhone(), "Alice", "Customer", false);
        customerA = customerRepository.save(customerA);

        String uuidB = UUID.randomUUID().toString().substring(0, 6);
        userB = new User("customer_b_" + uuidB + "@test.com", passwordEncoder.encode("Password123!"), "Bob", "Customer", "98472" + uuidB);
        userB.setRoles(Set.of(customerRole));
        userB = userRepository.save(userB);

        customerB = new Customer(userB, userB.getEmail(), userB.getPhone(), "Bob", "Customer", false);
        customerB = customerRepository.save(customerB);

        Brand brand = brandRepository.save(new Brand("TestBrand-" + uuidA, "brand-" + uuidA, "desc", null));
        Category category = categoryRepository.save(new Category("TestCat-" + uuidA, "cat-" + uuidA, "desc", null, 0));

        Product product = new Product(brand, category, "Pet Food Pack", "food-pack-" + uuidA, "SKU-" + uuidA, new BigDecimal("499.00"), new BigDecimal("449.00"));
        product = productRepository.save(product);

        // Order for Customer A
        orderCustomerA = new Order();
        orderCustomerA.setOrderNumber("ORD-TEST-A-" + uuidA);
        orderCustomerA.setCustomer(customerA);
        orderCustomerA.setTotalAmount(new BigDecimal("449.00"));
        orderCustomerA.setDiscountAmount(BigDecimal.ZERO);
        orderCustomerA.setDeliveryCharge(new BigDecimal("50.00"));
        orderCustomerA.setTaxAmount(BigDecimal.ZERO);
        orderCustomerA.setFinalPayableAmount(new BigDecimal("499.00"));
        orderCustomerA.setPaymentStatus("COMPLETED");
        orderCustomerA.setPaymentMethod("RAZORPAY");
        orderCustomerA.setOrderStatus("CONFIRMED");
        orderCustomerA.setDeliveryAddressJson("{\"city\":\"Thiruvananthapuram\",\"postalCode\":\"695003\"}");
        orderCustomerA.setShopDistanceKm(new BigDecimal("3.5"));
        orderCustomerA.setIsWithinRadius(true);
        orderCustomerA = orderRepository.save(orderCustomerA);

        itemWithoutHsn = new OrderItem();
        itemWithoutHsn.setOrder(orderCustomerA);
        itemWithoutHsn.setProduct(product);
        itemWithoutHsn.setProductName("Pet Food Pack");
        itemWithoutHsn.setBrandName("TestBrand");
        itemWithoutHsn.setSku("SKU-" + uuidA);
        itemWithoutHsn.setHsnCode(null); // Explicitly null HSN to verify no fallback to 2309
        itemWithoutHsn.setQuantity(1);
        itemWithoutHsn.setUnitPrice(new BigDecimal("449.00"));
        itemWithoutHsn.setTotalPrice(new BigDecimal("449.00"));
        itemWithoutHsn.setReturnPolicySnapshot("{\"returnAllowed\":true,\"returnWindowDays\":7}");
        itemWithoutHsn.setRefundPolicySnapshot("{\"refundAllowed\":true}");
        orderCustomerA.addItem(itemWithoutHsn);
        itemWithoutHsn = orderItemRepository.save(itemWithoutHsn);
        orderCustomerA = orderRepository.save(orderCustomerA);

        // Order for Customer B
        orderCustomerB = new Order();
        orderCustomerB.setOrderNumber("ORD-TEST-B-" + uuidB);
        orderCustomerB.setCustomer(customerB);
        orderCustomerB.setTotalAmount(new BigDecimal("899.00"));
        orderCustomerB.setDiscountAmount(BigDecimal.ZERO);
        orderCustomerB.setDeliveryCharge(BigDecimal.ZERO);
        orderCustomerB.setTaxAmount(BigDecimal.ZERO);
        orderCustomerB.setFinalPayableAmount(new BigDecimal("899.00"));
        orderCustomerB.setPaymentStatus("COMPLETED");
        orderCustomerB.setPaymentMethod("RAZORPAY");
        orderCustomerB.setOrderStatus("CONFIRMED");
        orderCustomerB.setDeliveryAddressJson("{\"city\":\"Thiruvananthapuram\",\"postalCode\":\"695003\"}");
        orderCustomerB.setShopDistanceKm(new BigDecimal("4.0"));
        orderCustomerB.setIsWithinRadius(true);
        orderCustomerB = orderRepository.save(orderCustomerB);

        OrderItem itemB = new OrderItem();
        itemB.setOrder(orderCustomerB);
        itemB.setProduct(product);
        itemB.setProductName("Pet Food Pack");
        itemB.setBrandName("TestBrand");
        itemB.setSku("SKU-" + uuidB);
        itemB.setHsnCode("23091000");
        itemB.setQuantity(2);
        itemB.setUnitPrice(new BigDecimal("449.00"));
        itemB.setTotalPrice(new BigDecimal("898.00"));
        itemB.setReturnPolicySnapshot("{\"returnAllowed\":true,\"returnWindowDays\":7}");
        itemB.setRefundPolicySnapshot("{\"refundAllowed\":true}");
        orderCustomerB.addItem(itemB);
        itemB = orderItemRepository.save(itemB);
        orderCustomerB = orderRepository.save(orderCustomerB);
    }

    @Test
    @DisplayName("Anonymous request to JSON invoice endpoint must be rejected with HTTP 401 Unauthorized")
    void anonymousRequestToJsonInvoice_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Customer A can retrieve their own JSON invoice successfully")
    void customerCanRetrieveOwnJsonInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(orderCustomerA.getOrderNumber()));
    }

    @Test
    @DisplayName("Anti-enumeration: Customer requesting unowned order returns HTTP 404 (same as nonexistent order)")
    void customerRequestingUnownedOrder_returns404() throws Exception {
        // Customer A requests Customer B's order -> HTTP 404 (prevents order existence oracle)
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerB.getOrderNumber() + "/invoice")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Anti-enumeration: Requesting nonexistent order returns HTTP 404")
    void nonexistentOrder_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/ORD-NONEXISTENT-999999/invoice")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "superadmin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Authorized Super Admin can access any customer's invoice")
    void superAdminCanAccessAnyInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(orderCustomerA.getOrderNumber()));
    }

    @Test
    @WithMockUser(username = "staffadmin@novapetzlife.com", authorities = {"ROLE_STAFF_ADMIN"})
    @DisplayName("Authorized Staff Admin can access customer invoices")
    void staffAdminCanAccessCustomerInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(orderCustomerA.getOrderNumber()));
    }

    @Test
    @DisplayName("Anonymous request to PDF invoice download endpoint must be rejected with 401")
    void anonymousRequestToPdfDownload_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PDF route alias /api/v1/invoices/{orderNumber}/download works for authenticated customer")
    void pdfDownloadRouteAlias_worksForAuthenticatedCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + orderCustomerA.getOrderNumber() + "/download")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Anti-enumeration: Cross-customer PDF download returns HTTP 404 (same as nonexistent)")
    void crossCustomerPdfDownload_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerB.getOrderNumber() + "/invoice/download")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "staffadmin@novapetzlife.com", authorities = {"ROLE_STAFF_ADMIN"})
    @DisplayName("Staff Admin can download PDF invoice for any customer order")
    void staffAdminCanDownloadPdfInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderCustomerA.getOrderNumber() + "/invoice/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Provisional receipt without GST does not fabricate 18% tax or fallback HSN 2309")
    void provisionalReceipt_noFabricatedTaxOrFallbackHsn() {
        InvoiceDto dto = invoiceService.getInvoiceDtoByOrderNumber(orderCustomerA.getOrderNumber());
        assertNotNull(dto);
        assertEquals("", dto.storeGstin(), "Store GSTIN must be empty when unconfigured");
        assertEquals(BigDecimal.ZERO, dto.totalCgst(), "CGST must be 0 for provisional receipt");
        assertEquals(BigDecimal.ZERO, dto.totalSgst(), "SGST must be 0 for provisional receipt");

        // Verify item with null HSN has null HSN (NOT "2309")
        InvoiceDto.InvoiceItemDto itemDto = dto.items().get(0);
        assertNull(itemDto.hsnCode(), "Item without HSN must not fall back to 2309");
        assertEquals(BigDecimal.ZERO, itemDto.cgstAmount());
        assertEquals(BigDecimal.ZERO, itemDto.sgstAmount());
    }

    @Test
    @DisplayName("Provisional PDF contains neutral provisional wording and excludes 'authentic GST Tax Invoice'")
    void provisionalPdf_containsNeutralNoticeAndExcludesGstClaim() throws Exception {
        InvoiceDto dto = invoiceService.getInvoiceDtoByOrderNumber(orderCustomerA.getOrderNumber());
        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
        byte[] pageContent = reader.getPageContent(1);
        String pageText = new String(pageContent, StandardCharsets.ISO_8859_1);
        reader.close();

        // Assert absence of false GST claims
        assertFalse(pageText.contains("authentic GST Tax Invoice"), "Provisional PDF must not contain 'authentic GST Tax Invoice'");
        assertFalse(pageText.contains("GST Status: Unregistered"), "Provisional PDF must not state 'GST Status: Unregistered'");

        // Assert presence of neutral title and notice
        assertTrue(pageText.contains("ORDER RECEIPT") || pageText.contains("PROVISIONAL"),
                "Provisional PDF must contain ORDER RECEIPT — PROVISIONAL title");
        assertTrue(pageText.contains("GST details are not configured"),
                "Provisional PDF must contain exact unconfigured notice");
    }

    @Test
    @DisplayName("Indian Currency Amount-in-Words generator produces exact words")
    void testAmountInWordsGenerator() {
        assertEquals("Indian Rupees One Thousand Four Hundred Ninety-Nine Only",
                InvoiceService.convertAmountToWords(new BigDecimal("1499.00")));

        assertEquals("Indian Rupees One Thousand Four Hundred Ninety-Nine and Fifty Paise Only",
                InvoiceService.convertAmountToWords(new BigDecimal("1499.50")));

        assertEquals("Indian Rupees Zero Only",
                InvoiceService.convertAmountToWords(BigDecimal.ZERO));

        assertEquals("Indian Rupees Two Lakh Fifty Thousand Only",
                InvoiceService.convertAmountToWords(new BigDecimal("250000.00")));
    }
}
