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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class InvoiceStabilityAndReconciliationIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TaxConfigurationRepository taxConfigRepository;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Customer account")));

        String uuid = UUID.randomUUID().toString().substring(0, 6);
        User user = new User("tax_user_" + uuid + "@test.com", passwordEncoder.encode("Pass123!"), "Tax", "User", "98471" + uuid);
        user.setRoles(Set.of(customerRole));
        user = userRepository.save(user);

        testCustomer = new Customer(user, user.getEmail(), user.getPhone(), "Tax", "User", false);
        testCustomer = customerRepository.save(testCustomer);

        Brand brand = brandRepository.save(new Brand("TaxBrand-" + uuid, "brand-" + uuid, "desc", null));
        Category category = categoryRepository.save(new Category("TaxCat-" + uuid, "cat-" + uuid, "desc", null, 0));

        testProduct = new Product(brand, category, "Premium Cat Food", "food-" + uuid, "SKU-" + uuid, new BigDecimal("500.00"), new BigDecimal("450.00"));
        testProduct = productRepository.save(testProduct);
    }

    private Order createOrder(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping) {
        BigDecimal finalPayable = subtotal.subtract(discount).add(shipping);
        Order order = new Order();
        order.setOrderNumber("ORD-INV-" + UUID.randomUUID().toString().substring(0, 8));
        order.setCustomer(testCustomer);
        order.setTotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setDeliveryCharge(shipping);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setFinalPayableAmount(finalPayable);
        order.setPaymentStatus("COMPLETED");
        order.setPaymentMethod("RAZORPAY");
        order.setOrderStatus("CONFIRMED");
        order.setDeliveryAddressJson("{\"city\":\"Thiruvananthapuram\",\"postalCode\":\"695003\"}");
        order.setShopDistanceKm(new BigDecimal("1.50"));
        order.setIsWithinRadius(true);
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(testProduct);
        item.setProductName(testProduct.getName());
        item.setBrandName("TaxBrand");
        item.setSku(testProduct.getSku());
        item.setQuantity(2);
        item.setUnitPrice(subtotal.divide(BigDecimal.valueOf(2)));
        item.setTotalPrice(subtotal);
        item.setReturnPolicySnapshot("{\"returnAllowed\":true,\"returnWindowDays\":7}");
        item.setRefundPolicySnapshot("{\"refundAllowed\":true}");
        orderItemRepository.save(item);
        order.getItems().add(item);

        return order;
    }

    @Test
    @DisplayName("Invoice discount reconciliation: Subtotal - Discount + Shipping strictly equals Grand Total")
    void invoiceDiscountReconciliation_strictlyBalances() {
        BigDecimal subtotal = new BigDecimal("1000.00");
        BigDecimal discount = new BigDecimal("150.00");
        BigDecimal shipping = new BigDecimal("50.00");
        BigDecimal expectedGrandTotal = new BigDecimal("900.00");

        Order order = createOrder(subtotal, discount, shipping);
        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);

        assertNotNull(dto);
        assertEquals(0, subtotal.compareTo(dto.totalTaxableValue()), "Pre-discount subtotal must match");
        assertEquals(0, discount.compareTo(dto.discountAmount()), "Discount amount must match");
        assertEquals(0, shipping.compareTo(dto.deliveryCharge()), "Delivery charge must match");
        assertEquals(0, expectedGrandTotal.compareTo(dto.grandTotal()), "Grand total must strictly equal subtotal - discount + shipping");
        assertEquals(0, expectedGrandTotal.compareTo(dto.getFinalPayableAmount()), "finalPayableAmount alias must match grandTotal");

        // Verify arithmetic reconciliation: Subtotal - Discount + Shipping == Grand Total
        BigDecimal computed = dto.totalTaxableValue().subtract(dto.discountAmount()).add(dto.deliveryCharge());
        assertEquals(dto.grandTotal(), computed, "Arithmetic must strictly balance without double deduction");

        // PDF Generation verification
        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF should be generated with content");
    }

    @Test
    @DisplayName("Historical invoice stability: Invoice generated before GST configuration remains PROVISIONAL_RECEIPT forever")
    void historicalInvoiceStability_persistsClassification() {
        // Ensure GSTIN is initially unconfigured
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");

        Order historicalOrder = createOrder(new BigDecimal("1200.00"), BigDecimal.ZERO, new BigDecimal("40.00"));
        Invoice historicalInvoice = invoiceService.generateInvoiceForOrder(historicalOrder);

        assertEquals("PROVISIONAL_RECEIPT", historicalInvoice.getDocumentType(),
                "Initial invoice generated without GSTIN must be classified as PROVISIONAL_RECEIPT");

        InvoiceDto initialDto = invoiceService.getInvoiceDtoByOrder(historicalOrder);
        assertEquals("PROVISIONAL_RECEIPT", initialDto.documentType());
        assertTrue(initialDto.storeGstin().isBlank());

        // Simulate later GSTIN configuration by store management
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "32AAACN0123M1Z5");
        assertTrue(invoiceService.hasConfiguredGstin(), "GSTIN is now configured");

        // Reload the historical invoice
        InvoiceDto reloadedDto = invoiceService.getInvoiceDtoByOrder(historicalOrder);

        // Historical invoice MUST NOT convert to GST_TAX_INVOICE!
        assertEquals("PROVISIONAL_RECEIPT", reloadedDto.documentType(),
                "Historical invoice must remain PROVISIONAL_RECEIPT even after GSTIN is configured");
        assertTrue(reloadedDto.storeGstin().isBlank(),
                "Historical invoice must not display GSTIN");

        // PDF for historical invoice must still be generated as PROVISIONAL RECEIPT
        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(reloadedDto);
        assertNotNull(pdfBytes);

        // Clean up: reset configuredGstin back to unconfigured
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }

    @Test
    @DisplayName("GST invoice safety: Non-empty GSTIN alone does not activate GST tax invoice when tax configuration is missing")
    void nonEmptyGstinAlone_doesNotActivateGstInvoice_whenTaxConfigurationsMissing() {
        // Configure valid GSTIN
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "32AAACN0123M1Z5");
        // Ensure tax configurations are empty
        taxConfigRepository.deleteAll();

        Order order = createOrder(new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("50.00"));
        Invoice invoice = invoiceService.generateInvoiceForOrder(order);

        assertEquals("PROVISIONAL_RECEIPT", invoice.getDocumentType(),
                "Non-empty GSTIN alone must never activate GST tax invoices when active tax configuration is absent");

        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);
        assertEquals("PROVISIONAL_RECEIPT", dto.documentType());
        assertTrue(dto.storeGstin().isBlank());

        // Reset
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }

    @Test
    @DisplayName("GST invoice safety: Missing or unconfigured product HSN code prevents GST tax invoice and generates PROVISIONAL_RECEIPT")
    void missingOrUnconfiguredItemHsn_preventsGstInvoice_andGeneratesProvisionalReceipt() {
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "32AAACN0123M1Z5");
        taxConfigRepository.deleteAll();
        // Active tax config for HSN 23091000 exists
        TaxConfiguration config = new TaxConfiguration("Pet Food", "23091000", new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO);
        taxConfigRepository.save(config);

        // Order item has NO HSN code (null)
        Order order = createOrder(new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        // createOrder creates items with null HSN by default
        Invoice invoice = invoiceService.generateInvoiceForOrder(order);

        assertEquals("PROVISIONAL_RECEIPT", invoice.getDocumentType(),
                "Order containing items without valid HSN codes must generate PROVISIONAL_RECEIPT");

        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);
        assertEquals("PROVISIONAL_RECEIPT", dto.documentType());

        // Reset
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }

    @Test
    @DisplayName("GST invoice safety: Valid GSTIN, active tax config, and verified product HSN activate GST invoice with dynamic rates")
    void validGstinAndTaxConfigAndHsn_activatesGstInvoice_withDynamicTaxRates() {
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "32AAACN0123M1Z5");
        taxConfigRepository.deleteAll();

        // 12% total GST (CGST 6%, SGST 6%) - proving no hardcoded 18% or 9% assumption
        TaxConfiguration config = new TaxConfiguration("Pet Supplement", "21069099", new BigDecimal("6.00"), new BigDecimal("6.00"), BigDecimal.ZERO);
        taxConfigRepository.save(config);

        BigDecimal gross = new BigDecimal("1120.00"); // 1000 base + 12% GST = 1120
        Order order = createOrder(gross, BigDecimal.ZERO, BigDecimal.ZERO);

        // Explicitly set verified HSN on the item
        OrderItem item = order.getItems().get(0);
        item.setHsnCode("21069099");
        orderItemRepository.save(item);

        Invoice invoice = invoiceService.generateInvoiceForOrder(order);
        assertEquals("GST_TAX_INVOICE", invoice.getDocumentType(),
                "Validated GSTIN, active tax configuration, and valid product HSN must activate GST_TAX_INVOICE");

        assertEquals(0, new BigDecimal("1000.00").compareTo(invoice.getTotalTaxableValue()),
                "Taxable value must be dynamically calculated at 12% (1120 / 1.12 = 1000)");
        assertEquals(0, new BigDecimal("60.00").compareTo(invoice.getTotalCgst()), "CGST must be 6% (60.00)");
        assertEquals(0, new BigDecimal("60.00").compareTo(invoice.getTotalSgst()), "SGST must be 6% (60.00)");

        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);
        assertEquals("GST_TAX_INVOICE", dto.documentType());
        assertEquals("32AAACN0123M1Z5", dto.storeGstin());
        assertEquals(1, dto.items().size());
        InvoiceDto.InvoiceItemDto itemDto = dto.items().get(0);
        assertEquals("21069099", itemDto.hsnCode());
        assertEquals(0, new BigDecimal("6.00").compareTo(itemDto.cgstRate()), "Item CGST rate must be dynamically 6.00");
        assertEquals(0, new BigDecimal("6.00").compareTo(itemDto.sgstRate()), "Item SGST rate must be dynamically 6.00");
        assertEquals(0, new BigDecimal("60.00").compareTo(itemDto.cgstAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(itemDto.sgstAmount()));

        // Reset
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }

    @Test
    @DisplayName("GST invoice safety: Malformed GSTIN format generates PROVISIONAL_RECEIPT")
    void invalidGstinFormat_generatesProvisionalReceipt() {
        // Invalid GSTIN string (not matching 15-char Indian GSTIN standard format)
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "INVALID_GSTIN_123");
        taxConfigRepository.deleteAll();
        TaxConfiguration config = new TaxConfiguration("Pet Food", "23091000", new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO);
        taxConfigRepository.save(config);

        Order order = createOrder(new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        OrderItem item = order.getItems().get(0);
        item.setHsnCode("23091000");
        orderItemRepository.save(item);

        Invoice invoice = invoiceService.generateInvoiceForOrder(order);
        assertEquals("PROVISIONAL_RECEIPT", invoice.getDocumentType(),
                "Malformed GSTIN must not activate GST tax invoices");

        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);
        assertEquals("PROVISIONAL_RECEIPT", dto.documentType());
        assertTrue(dto.storeGstin().isBlank());

        // Reset
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }

    @Test
    @DisplayName("NPZ-VERIFY-008: GST Tax Invoice line-item arithmetic reconciles post-discount: taxable + tax == totalAmount")
    void testGstInvoice_LineItemArithmetic_ReconcilesPostDiscount() {
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "32AAACN0123M1Z5");
        taxConfigRepository.deleteAll();

        // 18% GST (CGST 9%, SGST 9%)
        TaxConfiguration config = new TaxConfiguration("Pet Nutrition", "23091000", new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO);
        taxConfigRepository.save(config);

        // Gross = 1180.00 (1000 base + 180 tax), Discount = 118.00 (100 base + 18 tax discount)
        // Net payable = 1062.00 goods + 50.00 shipping = 1112.00
        BigDecimal gross = new BigDecimal("1180.00");
        BigDecimal discount = new BigDecimal("118.00");
        BigDecimal shipping = new BigDecimal("50.00");
        Order order = createOrder(gross, discount, shipping);

        OrderItem item = order.getItems().get(0);
        item.setHsnCode("23091000");
        orderItemRepository.save(item);

        InvoiceDto dto = invoiceService.getInvoiceDtoByOrder(order);
        assertNotNull(dto);
        assertEquals("GST_TAX_INVOICE", dto.documentType());

        // Verify line item reconciliation
        assertEquals(1, dto.items().size());
        InvoiceDto.InvoiceItemDto itemDto = dto.items().get(0);

        // Effective net gross after discount = 1180.00 - 118.00 = 1062.00
        BigDecimal expectedEffectiveGross = new BigDecimal("1062.00");
        BigDecimal expectedTaxable = new BigDecimal("900.00");
        BigDecimal expectedCgst = new BigDecimal("81.00");
        BigDecimal expectedSgst = new BigDecimal("81.00");

        assertEquals(0, expectedTaxable.compareTo(itemDto.taxableValue()), "Taxable value must reflect net post-discount (900.00)");
        assertEquals(0, expectedCgst.compareTo(itemDto.cgstAmount()), "CGST must be 9% of 900.00 (81.00)");
        assertEquals(0, expectedSgst.compareTo(itemDto.sgstAmount()), "SGST must be 9% of 900.00 (81.00)");
        assertEquals(0, expectedEffectiveGross.compareTo(itemDto.totalAmount()), "Item totalAmount must match net effective gross (1062.00)");

        // Strict line-item reconciliation assertion: taxableValue + cgst + sgst == totalAmount
        BigDecimal computedLineTotal = itemDto.taxableValue().add(itemDto.cgstAmount()).add(itemDto.sgstAmount());
        assertEquals(0, computedLineTotal.compareTo(itemDto.totalAmount()), "Line item taxableValue + tax must strictly equal totalAmount without discrepancy");

        // Financial summary totals reconciliation: taxable + cgst + sgst + shipping == grandTotal
        BigDecimal expectedGrandTotal = new BigDecimal("1112.00");
        assertEquals(0, expectedGrandTotal.compareTo(dto.grandTotal()), "Grand total must strictly match net + shipping (1112.00)");

        BigDecimal computedGrandTotal = dto.totalTaxableValue().add(dto.totalCgst()).add(dto.totalSgst()).add(dto.deliveryCharge());
        assertEquals(0, computedGrandTotal.compareTo(dto.grandTotal()), "Invoice summary totals must balance with grandTotal");

        // Verify PDF builds cleanly with reconciled amounts
        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        // Reset
        ReflectionTestUtils.setField(invoiceService, "configuredGstin", "");
    }
}
