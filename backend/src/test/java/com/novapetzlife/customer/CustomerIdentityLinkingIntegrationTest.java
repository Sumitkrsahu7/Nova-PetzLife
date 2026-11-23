package com.novapetzlife.customer;

import com.novapetzlife.petprofile.PetProfile;
import com.novapetzlife.petprofile.PetProfileRepository;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.RoleRepository;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserOAuthIdentity;
import com.novapetzlife.user.UserOAuthIdentityRepository;
import com.novapetzlife.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerIdentityLinkingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Autowired
    private PetProfileRepository petProfileRepository;

    @Autowired
    private com.novapetzlife.support.SupportService supportService;

    @Autowired
    private com.novapetzlife.order.OrderRepository orderRepository;

    @Autowired
    private com.novapetzlife.notification.NotificationRepository notificationRepository;

    @Autowired
    private com.novapetzlife.support.SupportTicketRepository supportTicketRepository;

    @Autowired
    private com.novapetzlife.support.SupportMessageRepository supportMessageRepository;

    private Role roleCustomer;

    @BeforeEach
    void setUp() {
        cleanAll();
        roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        supportMessageRepository.deleteAll();
        supportTicketRepository.deleteAll();
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        petProfileRepository.deleteAll();
        userOAuthIdentityRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Fix C.1: Verified Google OAuth user with exactly ONE eligible unlinked guest record links safely")
    void testVerifiedGoogleOAuthLinksSingleGuest() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "verified.oauth." + suffix + "@gmail.com";

        // 1. Guest creates order/profile previously (unlinked)
        Customer guest = customerService.createGuestCustomer(email, "+919447100001", "Priya", "Nair");
        assertNull(guest.getUser(), "Guest customer must have null user initially");
        assertTrue(guest.getIsGuest(), "Guest customer must have isGuest=true");

        // Register pet for guest
        PetProfile guestPet = petProfileRepository.save(new PetProfile(
                guest, "Coco", "Dog", "Poodle", LocalDate.now().minusYears(1),
                "Female", new BigDecimal("6.0"), "Adult", "Kibble", "None"
        ));

        // 2. User registers/authenticates via Google OAuth
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setFirstName("Priya");
        user.setLastName("Nair");
        user.setPhone("+919447100001");
        user.setRoles(Set.of(roleCustomer));
        user = userRepository.save(user);

        // Attach verified Google OAuth identity
        UserOAuthIdentity googleIdentity = new UserOAuthIdentity(user, "GOOGLE", "sub_google_" + suffix, email);
        userOAuthIdentityRepository.save(googleIdentity);

        // 3. Resolve customer for user
        Customer resolved = customerService.getOrCreateCustomerForUser(user);

        // Assert that the guest customer was safely linked
        assertEquals(guest.getId(), resolved.getId(), "Verified OAuth user must link to the single matching guest record");
        assertNotNull(resolved.getUser(), "Resolved customer must now reference the user");
        assertEquals(user.getId(), resolved.getUser().getId());
        assertFalse(resolved.getIsGuest(), "Linked customer must now have isGuest=false");

        // Assert that guest pets are now accessible to this user
        List<PetProfile> customerPets = petProfileRepository.findCustomerPets(resolved.getId(), user.getId());
        assertEquals(1, customerPets.size(), "Linked user must now access the registered pet");
        assertEquals("Coco", customerPets.get(0).getPetName());
    }

    @Test
    @DisplayName("Fix C.2: Unverified user (standard local signup) does NOT automatically claim guest records")
    void testUnverifiedUserDoesNotLinkGuestRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "unverified.local." + suffix + "@example.com";

        // 1. Guest creates profile previously
        Customer guest = customerService.createGuestCustomer(email, "+919447100002", "Deepak", "Kumar");
        Long guestId = guest.getId();

        // Register pet for guest
        PetProfile guestPet = petProfileRepository.save(new PetProfile(
                guest, "Simba", "Cat", "Domestic", LocalDate.now().minusYears(2),
                "Male", new BigDecimal("4.0"), "Adult", "Fish", "None"
        ));

        // 2. Unverified local user signs up with the same email (no OAuth identity)
        User unverifiedUser = new User();
        unverifiedUser.setEmail(email);
        unverifiedUser.setPasswordHash("$2a$10$hashedPassword");
        unverifiedUser.setFirstName("Deepak");
        unverifiedUser.setLastName("Kumar");
        unverifiedUser.setPhone("+919447100002");
        unverifiedUser.setRoles(Set.of(roleCustomer));
        unverifiedUser = userRepository.save(unverifiedUser);

        // 3. Resolve customer profile for unverified user
        Customer resolved = customerService.getOrCreateCustomerForUser(unverifiedUser);

        // Assert that unverified user does NOT hijack guest record
        assertNotEquals(guestId, resolved.getId(), "Unverified user must NOT automatically claim existing guest record");
        assertEquals(unverifiedUser.getId(), resolved.getUser().getId());

        // Guest customer record remains untouched and unlinked
        Customer guestInDb = customerRepository.findById(guestId).orElseThrow();
        assertNull(guestInDb.getUser(), "Guest record must remain unlinked");
        assertTrue(guestInDb.getIsGuest());

        // Guest pet must NOT be accessible to this unverified user
        List<PetProfile> userPets = petProfileRepository.findCustomerPets(resolved.getId(), unverifiedUser.getId());
        assertTrue(userPets.isEmpty(), "Unverified user must not see guest pets");
    }

    @Test
    @DisplayName("Fix C.3: Multiple guest records with same email are detected as ambiguous and NOT arbitrarily linked")
    void testMultipleGuestRecordsAreNotArbitrarilyLinked() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "duplicate.guest." + suffix + "@example.com";

        // Create TWO guest records with the same email
        Customer guest1 = customerService.createGuestCustomer(email, "+919447100003", "GuestOne", "Petz");
        Customer guest2 = customerService.createGuestCustomer(email, "+919447100004", "GuestTwo", "Petz");

        assertNotEquals(guest1.getId(), guest2.getId());

        // Verified OAuth user signs up with this email
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setFirstName("Verified");
        user.setLastName("User");
        user.setRoles(Set.of(roleCustomer));
        user = userRepository.save(user);

        UserOAuthIdentity googleIdentity = new UserOAuthIdentity(user, "GOOGLE", "sub_dup_" + suffix, email);
        userOAuthIdentityRepository.save(googleIdentity);

        // Resolving customer must detect ambiguity and create a fresh customer rather than picking one arbitrarily
        Customer resolved = customerService.getOrCreateCustomerForUser(user);

        assertNotNull(resolved);
        assertNotEquals(guest1.getId(), resolved.getId(), "Must not arbitrarily link to guest1");
        assertNotEquals(guest2.getId(), resolved.getId(), "Must not arbitrarily link to guest2");
        assertEquals(user.getId(), resolved.getUser().getId());

        // Both guest records remain safe and unmutated
        assertNull(customerRepository.findById(guest1.getId()).orElseThrow().getUser());
        assertNull(customerRepository.findById(guest2.getId()).orElseThrow().getUser());
    }

    @Test
    @DisplayName("Fix C.4: Email normalization handles case differences and whitespace consistently")
    void testEmailNormalization() {
        String raw1 = "  MyEmail.Test@Example.COM  ";
        String raw2 = "myemail.test@example.com";

        assertEquals(CustomerService.normalizeEmail(raw2), CustomerService.normalizeEmail(raw1));
        assertNull(CustomerService.normalizeEmail("   "));
        assertNull(CustomerService.normalizeEmail(null));
    }

    @Test
    @DisplayName("Fix C.5: findByEmail default method does not throw NonUniqueResultException on duplicate guest records")
    void testFindByEmailHandlesDuplicatesWithoutCrashing() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String sharedEmail = "dup.repo." + suffix + "@example.com";

        customerService.createGuestCustomer(sharedEmail, "+919447100005", "Dup1", "A");
        customerService.createGuestCustomer(sharedEmail, "+919447100006", "Dup2", "B");

        // Calling findAllByEmailIgnoreCase returns both
        List<Customer> all = customerRepository.findAllByEmailIgnoreCase(sharedEmail);
        assertEquals(2, all.size());

        // Calling findByEmail must safely return an Optional without throwing NonUniqueResultException
        assertDoesNotThrow(() -> {
            var first = customerRepository.findByEmail(sharedEmail);
            assertTrue(first.isPresent());
        });
    }

    @Test
    @DisplayName("Fix 3.1: Support inquiry from unauthenticated user never binds existing registered customer")
    void testSupportInquiryDoesNotLeakExistingCustomerRecordForUnauthenticatedUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String victimEmail = "dr.ramesh." + suffix + "@example.com";

        // Create an existing registered customer
        User regUser = new User();
        regUser.setEmail(victimEmail);
        regUser.setPasswordHash("$2a$10$dummyHash");
        regUser.setFirstName("Ramesh");
        regUser.setLastName("Nair");
        regUser.setPhone("+919447111111");
        regUser.setRoles(Set.of(roleCustomer));
        regUser = userRepository.save(regUser);

        Customer existingCustomer = new Customer(regUser, victimEmail, "+919447111111", "Ramesh", "Nair", false);
        existingCustomer = customerRepository.save(existingCustomer);

        // An unauthenticated user submits an inquiry with victim's email but different name and phone
        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(victimEmail);
        req.setName("Guest Inquirer");
        req.setPhone("+919999999999");
        req.setSubject("Question about dog food");
        req.setMessage("Is pedigree suitable for 3 month old pups?");
        req.setPriority("MEDIUM");

        var ticketDto = supportService.createInquiry(req, null);

        // Verify ticket does NOT link to existingCustomer's ID
        assertNotNull(ticketDto.getId());
        assertNotEquals(existingCustomer.getId(), ticketDto.getCustomerId(), "Support inquiry must not claim existing customer record");

        // Existing customer remains unaffected
        Customer after = customerRepository.findById(existingCustomer.getId()).orElseThrow();
        assertEquals("Ramesh", after.getFirstName());
        assertEquals("+919447111111", after.getPhone());
    }

    @Test
    @DisplayName("Req 1.1: Authenticated customer attaching their own order attaches successfully")
    void testAuthenticatedCustomerAttachingOwnOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String customerEmail = "auth.owner." + suffix + "@example.com";

        User user = new User();
        user.setEmail(customerEmail);
        user.setPasswordHash("$2a$10$dummyHash");
        user.setFirstName("Kavitha");
        user.setLastName("Menon");
        user.setRoles(Set.of(roleCustomer));
        user = userRepository.save(user);

        Customer customer = new Customer(user, customerEmail, "+919447444441", "Kavitha", "Menon", false);
        customer = customerRepository.save(customer);

        com.novapetzlife.order.Order myOrder = new com.novapetzlife.order.Order(
                "ORD-MY-" + suffix.toUpperCase(), customer, "CONFIRMED",
                new BigDecimal("650.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("650.00"),
                "PAID", "COD", "{}", new BigDecimal("2.5"), true, "Standard"
        );
        myOrder = orderRepository.save(myOrder);

        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(customerEmail);
        req.setName("Kavitha Menon");
        req.setSubject("Delivery Question");
        req.setMessage("When will my order arrive?");
        req.setOrderNumber(myOrder.getOrderNumber());
        req.setPriority("MEDIUM");

        var ticketDto = supportService.createInquiry(req, customer);

        assertNotNull(ticketDto.getId());
        assertEquals(myOrder.getOrderNumber(), ticketDto.getOrderNumber(), "Authenticated customer's own order must be attached");
        assertEquals("CONFIRMED", ticketDto.getOrderStatus());
    }

    @Test
    @DisplayName("Req 1.2: Authenticated customer attempting to attach another customer's order is rejected without disclosure")
    void testAuthenticatedCustomerAttemptingToAttachAnotherCustomersOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String custAEmail = "cust.a." + suffix + "@example.com";
        String custBEmail = "cust.b." + suffix + "@example.com";

        Customer customerA = customerRepository.save(new Customer(null, custAEmail, "+919447444442", "Alice", "A", false));
        Customer customerB = customerRepository.save(new Customer(null, custBEmail, "+919447444443", "Bob", "B", false));

        com.novapetzlife.order.Order bobOrder = new com.novapetzlife.order.Order(
                "ORD-BOB-" + suffix.toUpperCase(), customerB, "DELIVERED",
                new BigDecimal("1200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1200.00"),
                "PAID", "ONLINE", "{}", new BigDecimal("3.0"), true, "Standard"
        );
        bobOrder = orderRepository.save(bobOrder);

        // Customer A submits an inquiry attempting to attach Bob's order
        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(custAEmail);
        req.setName("Alice A");
        req.setSubject("Looking into Bob's order");
        req.setMessage("Checking status");
        req.setOrderNumber(bobOrder.getOrderNumber());
        req.setPriority("HIGH");

        var ticketDto = supportService.createInquiry(req, customerA);

        assertNotNull(ticketDto.getId());
        assertNull(ticketDto.getOrderNumber(), "Customer A must NEVER be permitted to attach Customer B's order");
        assertNull(ticketDto.getOrderStatus());
        assertNull(ticketDto.getOrderFinalAmount());
    }

    @Test
    @DisplayName("Req 1.3: Guest inquiry attempting to attach existing order with correct email and order number does not attach order entity")
    void testGuestInquiryAttemptingToAttachExistingOrderUsingCorrectEmailAndOrderNumber() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String guestEmail = "target.guest." + suffix + "@example.com";

        Customer legitimateCustomer = customerRepository.save(new Customer(null, guestEmail, "+919447444444", "Rahul", "V", false));

        com.novapetzlife.order.Order existingOrder = new com.novapetzlife.order.Order(
                "ORD-GUEST-" + suffix.toUpperCase(), legitimateCustomer, "PROCESSING",
                new BigDecimal("800.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("800.00"),
                "PAID", "COD", "{}", new BigDecimal("1.5"), true, "Standard"
        );
        existingOrder = orderRepository.save(existingOrder);

        // Unauthenticated guest submits inquiry using matching email and matching order number
        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(guestEmail);
        req.setName("Unverified Guest");
        req.setPhone("+919447999999");
        req.setSubject("Status query");
        req.setMessage("Please provide an update");
        req.setOrderNumber(existingOrder.getOrderNumber());
        req.setPriority("MEDIUM");

        var ticketDto = supportService.createInquiry(req, null);

        assertNotNull(ticketDto.getId());
        // Persistent Order entity must NOT be attached to unauthenticated inquiry
        assertNull(ticketDto.getOrderNumber(), "Unauthenticated guest inquiry must NOT attach persistent Order entity");
        assertNull(ticketDto.getOrderStatus());
        assertNull(ticketDto.getOrderFinalAmount());

        // Legitimate inquiry is preserved and isolated guest customer created
        assertNotEquals(legitimateCustomer.getId(), ticketDto.getCustomerId());
    }

    @Test
    @DisplayName("Req 1.4: Guest inquiry using another customer's email creates isolated guest without attaching order")
    void testGuestInquiryUsingAnotherCustomersEmail() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String victimEmail = "victim." + suffix + "@example.com";

        Customer victim = customerRepository.save(new Customer(null, victimEmail, "+919447444445", "Victim", "User", false));

        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(victimEmail);
        req.setName("Attacker Spoofing Victim");
        req.setPhone("+919999888877");
        req.setSubject("Product doubt");
        req.setMessage("Do you have stock of royal canin?");
        req.setPriority("LOW");

        var ticketDto = supportService.createInquiry(req, null);

        assertNotNull(ticketDto.getId());
        assertNotEquals(victim.getId(), ticketDto.getCustomerId(), "Inquiry must not claim victim's customer record");
        assertNull(ticketDto.getOrderNumber());
    }

    @Test
    @DisplayName("Req 1.5: Inquiry with invalid order number preserves inquiry without disclosing non-existence")
    void testInquiryWithInvalidOrderNumber() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String custEmail = "inval.order." + suffix + "@example.com";

        Customer customer = customerRepository.save(new Customer(null, custEmail, "+919447444446", "Manoj", "K", false));

        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(custEmail);
        req.setName("Manoj K");
        req.setSubject("Wrong Order Number");
        req.setMessage("I might have mistyped the order number");
        req.setOrderNumber("ORD-DOES-NOT-EXIST-99999");
        req.setPriority("LOW");

        var ticketDto = supportService.createInquiry(req, customer);

        assertNotNull(ticketDto.getId());
        assertNull(ticketDto.getOrderNumber(), "Invalid order number must result in order being null");
    }

    @Test
    @DisplayName("Req 1.6: Guest inquiry without order reference is preserved successfully")
    void testGuestInquiryWithoutOrderReference() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String guestEmail = "pure.guest." + suffix + "@example.com";

        com.novapetzlife.support.dto.CreateInquiryRequest req = new com.novapetzlife.support.dto.CreateInquiryRequest();
        req.setEmail(guestEmail);
        req.setName("Pure Guest");
        req.setSubject("Store Timings");
        req.setMessage("What time does the Kuravankonam shop open on Sundays?");
        req.setPriority("LOW");
        req.setOrderNumber(null);

        var ticketDto = supportService.createInquiry(req, null);

        assertNotNull(ticketDto.getId());
        assertNull(ticketDto.getOrderNumber());
        assertEquals("Pure Guest", ticketDto.getCustomerName());
    }

    @Test
    @DisplayName("Req 2.1: NotificationController returns HTTP 401 Unauthorized for unauthenticated requests")
    void testNotificationControllerUnauthenticatedReceives401() throws Exception {
        mockMvc.perform(get("/api/v1/customer/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Req 2.2: Customer A cannot access Customer B's notifications even with customerId parameter")
    void testNotificationControllerCustomerACannotAccessCustomerBNotifications() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String userAEmail = "user.a." + suffix + "@example.com";
        String userBEmail = "user.b." + suffix + "@example.com";

        User userA = new User();
        userA.setEmail(userAEmail);
        userA.setPasswordHash("$2a$10$dummyHash");
        userA.setFirstName("Alice");
        userA.setLastName("A");
        userA.setRoles(Set.of(roleCustomer));
        userA = userRepository.save(userA);
        Customer custA = customerRepository.save(new Customer(userA, userAEmail, "+919447555551", "Alice", "A", false));

        User userB = new User();
        userB.setEmail(userBEmail);
        userB.setPasswordHash("$2a$10$dummyHash");
        userB.setFirstName("Bob");
        userB.setLastName("B");
        userB.setRoles(Set.of(roleCustomer));
        userB = userRepository.save(userB);
        Customer custB = customerRepository.save(new Customer(userB, userBEmail, "+919447555552", "Bob", "B", false));

        // Create a notification for Customer B
        com.novapetzlife.notification.Notification notifB = new com.novapetzlife.notification.Notification(
                custB, userBEmail, "EMAIL", "ORDER_CONFIRMATION", "Secret Order For Bob", "Content for Bob", "SENT"
        );
        notifB.setSentAt(java.time.Instant.now());
        notificationRepository.save(notifB);

        // Authenticated Customer A requests notifications with ?customerId={custB.id}
        mockMvc.perform(get("/api/v1/customer/notifications")
                        .with(user(userA.getEmail()).roles("CUSTOMER"))
                        .param("customerId", custB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", empty())); // Alice cannot see Bob's notification!
    }

    @Test
    @DisplayName("Req 2.3: Admin can access customer notifications using customerId parameter")
    void testNotificationControllerAdminCanAccessCustomerNotifications() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String userBEmail = "user.admin.b." + suffix + "@example.com";

        Role roleAdmin = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_SUPER_ADMIN", "Super Admin")));

        User userB = new User();
        userB.setEmail(userBEmail);
        userB.setPasswordHash("$2a$10$dummyHash");
        userB.setFirstName("Charlie");
        userB.setLastName("C");
        userB.setRoles(Set.of(roleCustomer));
        userB = userRepository.save(userB);
        Customer custB = customerRepository.save(new Customer(userB, userBEmail, "+919447555553", "Charlie", "C", false));

        com.novapetzlife.notification.Notification notifB = new com.novapetzlife.notification.Notification(
                custB, userBEmail, "SMS", "DISPATCH", "Package on the way", "Dispatched to Charlie", "SENT"
        );
        notifB.setSentAt(java.time.Instant.now());
        notificationRepository.save(notifB);

        // Admin requests notifications with ?customerId={custB.id}
        mockMvc.perform(get("/api/v1/customer/notifications")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN"))
                        .param("customerId", custB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].subject").value("Package on the way"));
    }
}
