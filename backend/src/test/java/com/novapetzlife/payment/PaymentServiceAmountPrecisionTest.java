package com.novapetzlife.payment;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.payment.dto.CreatePaymentOrderRequest;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceAmountPrecisionTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private RazorpayClientService razorpayClientService;

    @SpyBean
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PaymentRepository paymentRepository;

    private Authentication customerAuth;
    private Customer testCustomer;

    private Order createPersistedOrder(String orderNumber, BigDecimal amount) {
        Order o = new Order();
        o.setOrderNumber(orderNumber);
        o.setCustomer(testCustomer);
        o.setOrderStatus("PENDING_PAYMENT");
        o.setPaymentStatus("PENDING");
        o.setPaymentMethod("RAZORPAY");
        o.setTotalAmount(amount != null ? amount : BigDecimal.ZERO);
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setDeliveryCharge(BigDecimal.ZERO);
        o.setTaxAmount(BigDecimal.ZERO);
        o.setFinalPayableAmount(amount);
        o.setDeliveryAddressJson("{\"addressLine1\":\"Pattom\"}");
        o.setShopDistanceKm(new BigDecimal("1.0"));
        o.setIsWithinRadius(true);
        return orderRepository.save(o);
    }

    private Order createInMemOrder(String orderNumber, BigDecimal amount) {
        User user = new User("aditya.precision@example.com", "Password@123", "Aditya", "Precision", "+919876543210");
        Customer customer = new Customer(user, "aditya.precision@example.com", "+919876543210", "Aditya", "Precision", false);
        Order o = new Order();
        o.setId(9999L);
        o.setOrderNumber(orderNumber);
        o.setCustomer(customer);
        o.setOrderStatus("PENDING_PAYMENT");
        o.setPaymentStatus("PENDING");
        o.setPaymentMethod("RAZORPAY");
        o.setTotalAmount(amount != null ? amount : BigDecimal.ZERO);
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setDeliveryCharge(BigDecimal.ZERO);
        o.setTaxAmount(BigDecimal.ZERO);
        o.setFinalPayableAmount(amount);
        o.setDeliveryAddressJson("{\"addressLine1\":\"Pattom\"}");
        o.setShopDistanceKm(new BigDecimal("1.0"));
        o.setIsWithinRadius(true);
        return o;
    }

    @BeforeEach
    void setUp() {
        customerAuth = new UsernamePasswordAuthenticationToken(
                "aditya.precision@example.com", "password", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        User user = userRepository.findByEmail("aditya.precision@example.com").orElseGet(() ->
                userRepository.save(new User("aditya.precision@example.com", "Password@123", "Aditya", "Precision", "+919876543210"))
        );
        this.testCustomer = customerService.getOrCreateCustomerForUser(user);
    }

    @Test
    @DisplayName("Should convert valid ₹100.00 to exact 10000 paise")
    void testAmount_100_00_Exact() {
        String orderNum = "ORD-PREC-100-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("100.00"));

        when(razorpayClientService.createOrder(anyString(), eq(10000L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayOrderResult("order_prec_100", 10000L, "INR"));

        var response = paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()));
        assertEquals(10000L, response.getAmountInPaise());
    }

    @Test
    @DisplayName("Should convert valid ₹100.50 to exact 10050 paise")
    void testAmount_100_50_Exact() {
        String orderNum = "ORD-PREC-10050-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("100.50"));

        when(razorpayClientService.createOrder(anyString(), eq(10050L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayOrderResult("order_prec_100_50", 10050L, "INR"));

        var response = paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()));
        assertEquals(10050L, response.getAmountInPaise());
    }

    @Test
    @DisplayName("Should convert valid ₹100.01 to exact 10001 paise")
    void testAmount_100_01_Exact() {
        String orderNum = "ORD-PREC-10001-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("100.01"));

        when(razorpayClientService.createOrder(anyString(), eq(10001L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayOrderResult("order_prec_100_01", 10001L, "INR"));

        var response = paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()));
        assertEquals(10001L, response.getAmountInPaise());
    }

    @Test
    @DisplayName("Should convert valid ₹100.99 to exact 10099 paise")
    void testAmount_100_99_Exact() {
        String orderNum = "ORD-PREC-10099-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("100.99"));

        when(razorpayClientService.createOrder(anyString(), eq(10099L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayOrderResult("order_prec_100_99", 10099L, "INR"));

        var response = paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()));
        assertEquals(10099L, response.getAmountInPaise());
    }

    @Test
    @DisplayName("Should convert valid ₹0.01 minimum to exact 1 paise")
    void testAmount_0_01_Exact() {
        String orderNum = "ORD-PREC-001-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("0.01"));

        when(razorpayClientService.createOrder(anyString(), eq(1L), anyString(), any()))
                .thenReturn(new RazorpayClientService.GatewayOrderResult("order_prec_001", 1L, "INR"));

        var response = paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()));
        assertEquals(1L, response.getAmountInPaise());
    }

    @Test
    @DisplayName("Should reject ₹100.001 with fractional paise (IllegalArgumentException)")
    void testAmount_100_001_FractionalPaise_Rejected() {
        String orderNum = "ORD-PREC-FRAC-" + UUID.randomUUID().toString().substring(0, 6);
        Order inMem = createInMemOrder(orderNum, new BigDecimal("100.001"));
        doReturn(Optional.of(inMem)).when(orderRepository).findByOrderNumber(orderNum);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(orderNum))
        );
        assertTrue(ex.getMessage().contains("cannot have fractional paise"));
    }

    @Test
    @DisplayName("Should reject ₹100.0001 with 4-decimal fractional paise (IllegalArgumentException)")
    void testAmount_100_0001_FractionalPaise_Rejected() {
        String orderNum = "ORD-PREC-FRAC4-" + UUID.randomUUID().toString().substring(0, 6);
        Order inMem = createInMemOrder(orderNum, new BigDecimal("100.0001"));
        doReturn(Optional.of(inMem)).when(orderRepository).findByOrderNumber(orderNum);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(orderNum))
        );
        assertTrue(ex.getMessage().contains("cannot have fractional paise"));
    }

    @Test
    @DisplayName("Should reject ₹0.00 with non-positive validation (IllegalArgumentException)")
    void testAmount_0_00_Zero_Rejected() {
        String orderNum = "ORD-PREC-ZERO-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, BigDecimal.ZERO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()))
        );
        assertTrue(ex.getMessage().contains("must be positive"));
    }

    @Test
    @DisplayName("Should reject negative amount ₹-50.00 (IllegalArgumentException)")
    void testAmount_Negative_Rejected() {
        String orderNum = "ORD-PREC-NEG-" + UUID.randomUUID().toString().substring(0, 6);
        Order saved = createPersistedOrder(orderNum, new BigDecimal("-50.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(saved.getOrderNumber()))
        );
        assertTrue(ex.getMessage().contains("must be positive"));
    }

    @Test
    @DisplayName("Should reject arithmetic overflow amount exceeding integer range")
    void testAmount_ArithmeticOverflow_Rejected() {
        String orderNum = "ORD-PREC-OVERFLOW-" + UUID.randomUUID().toString().substring(0, 6);
        Order inMem = createInMemOrder(orderNum, new BigDecimal("10000000000000000000.00"));
        doReturn(Optional.of(inMem)).when(orderRepository).findByOrderNumber(orderNum);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentOrder(customerAuth, new CreatePaymentOrderRequest(orderNum))
        );
        assertTrue(ex.getMessage().contains("arithmetic overflow"));
    }

    @Test
    @DisplayName("Direct arithmetic conversion rejects fractional paise with ArithmeticException when scale UNNECESSARY is violated")
    void testDirectArithmetic_RoundingModeUnnecessary() {
        BigDecimal fractional = new BigDecimal("100.001");
        assertThrows(ArithmeticException.class, () ->
                fractional.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY)
        );
    }
}
