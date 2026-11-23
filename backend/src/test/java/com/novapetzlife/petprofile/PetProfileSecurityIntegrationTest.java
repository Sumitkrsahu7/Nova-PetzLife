package com.novapetzlife.petprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PetProfileSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PetProfileRepository petProfileRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PetProfileService petProfileService;

    private User userA;
    private Customer customerA;
    private PetProfile petA;

    private User userB;
    private Customer customerB;
    private PetProfile petB;

    @BeforeEach
    void setUp() {
        petProfileRepository.deleteAll();

        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));
        Role roleAdmin = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_SUPER_ADMIN", "Administrator")));

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // Setup Customer A
        userA = userRepository.findByEmail("customer.sec.a@example.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("customer.sec.a@example.com");
            u.setPasswordHash("$2a$10$dummyHash");
            u.setFirstName("Alice");
            u.setLastName("Owner");
            u.setPhone("+9194471" + suffix);
            u.setRoles(Set.of(roleCustomer));
            return userRepository.save(u);
        });
        customerA = customerRepository.findByUserId(userA.getId()).orElseGet(() -> {
            Customer c = new Customer(userA, userA.getEmail(), userA.getPhone(), "Alice", "Owner", false);
            return customerRepository.save(c);
        });

        petA = new PetProfile(
                customerA,
                "Fluffy",
                "Cat",
                "Persian",
                LocalDate.now().minusYears(1),
                "Female",
                new BigDecimal("4.2"),
                "Adult",
                "Tuna",
                "None"
        );
        petA = petProfileRepository.save(petA);

        // Setup Customer B
        userB = userRepository.findByEmail("customer.sec.b@example.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("customer.sec.b@example.com");
            u.setPasswordHash("$2a$10$dummyHash");
            u.setFirstName("Bob");
            u.setLastName("Owner");
            u.setPhone("+9194472" + suffix);
            u.setRoles(Set.of(roleCustomer));
            return userRepository.save(u);
        });
        customerB = customerRepository.findByUserId(userB.getId()).orElseGet(() -> {
            Customer c = new Customer(userB, userB.getEmail(), userB.getPhone(), "Bob", "Owner", false);
            return customerRepository.save(c);
        });

        petB = new PetProfile(
                customerB,
                "Spike",
                "Dog",
                "Bulldog",
                LocalDate.now().minusYears(3),
                "Male",
                new BigDecimal("22.5"),
                "Adult",
                "Beef",
                "Grain"
        );
        petB = petProfileRepository.save(petB);
    }

    // 1. Unauthenticated caller cannot list customer pets
    @Test
    @DisplayName("Fix A.1: Unauthenticated request to list pets is rejected with 401 Unauthorized")
    void testUnauthenticatedCannotListPets() throws Exception {
        mockMvc.perform(get("/api/v1/customer/pets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // 2. Unauthenticated caller cannot retrieve a pet by ID
    @Test
    @DisplayName("Fix A.2: Unauthenticated request to get pet by ID is rejected with 401 Unauthorized")
    void testUnauthenticatedCannotGetPetById() throws Exception {
        mockMvc.perform(get("/api/v1/customer/pets/" + petA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // 3. Unauthenticated caller cannot update a pet by ID
    @Test
    @DisplayName("Fix A.3: Unauthenticated request to update pet is rejected with 401 Unauthorized")
    void testUnauthenticatedCannotUpdatePetById() throws Exception {
        CreatePetProfileRequest req = new CreatePetProfileRequest(
                "HackedFluffy", "Cat", "Persian", LocalDate.now().minusYears(1),
                "Female", new BigDecimal("5.0"), "Adult", "Milk", "None"
        );

        mockMvc.perform(put("/api/v1/customer/pets/" + petA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // 4. Unauthenticated caller cannot delete a pet by ID
    @Test
    @DisplayName("Fix A.4: Unauthenticated request to delete pet is rejected with 401 Unauthorized")
    void testUnauthenticatedCannotDeletePetById() throws Exception {
        mockMvc.perform(delete("/api/v1/customer/pets/" + petA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // 5. Supplying ?customerId=123 does not bypass authentication or ownership
    @Test
    @DisplayName("Fix A.5: Supplying ?customerId parameter does not bypass authentication")
    void testCustomerIdParamDoesNotBypassAuth() throws Exception {
        mockMvc.perform(get("/api/v1/customer/pets?customerId=" + customerA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/customer/pets/" + petA.getId() + "?customerId=" + customerA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // 6. Customer A cannot view Customer B's pets
    @Test
    @WithMockUser(username = "customer.sec.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix A.6: Authenticated Customer A cannot view Customer B's pet profile (403 Forbidden)")
    void testCustomerACannotViewCustomerBPet() throws Exception {
        mockMvc.perform(get("/api/v1/customer/pets/" + petB.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // 7. Customer A cannot update Customer B's pets
    @Test
    @WithMockUser(username = "customer.sec.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix A.7: Authenticated Customer A cannot update Customer B's pet profile (403 Forbidden)")
    void testCustomerACannotUpdateCustomerBPet() throws Exception {
        CreatePetProfileRequest req = new CreatePetProfileRequest(
                "HijackedSpike", "Dog", "Bulldog", LocalDate.now().minusYears(3),
                "Male", new BigDecimal("25.0"), "Adult", "None", "None"
        );

        mockMvc.perform(put("/api/v1/customer/pets/" + petB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Verify Spike in database remains unmodified
        PetProfile spikeInDb = petProfileRepository.findById(petB.getId()).orElseThrow();
        assertEquals("Spike", spikeInDb.getPetName());
    }

    // 8. Customer A cannot delete Customer B's pets
    @Test
    @WithMockUser(username = "customer.sec.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix A.8: Authenticated Customer A cannot delete Customer B's pet profile (403 Forbidden)")
    void testCustomerACannotDeleteCustomerBPet() throws Exception {
        mockMvc.perform(delete("/api/v1/customer/pets/" + petB.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Verify Spike in database remains intact
        assertTrue(petProfileRepository.existsById(petB.getId()));
    }

    // 9. Customer A cannot change ownership through manipulated request body or params
    @Test
    @WithMockUser(username = "customer.sec.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix A.9: Ownership is strictly bound to authenticated principal; cannot change customer ownership")
    void testCustomerCannotChangeOwnership() throws Exception {
        CreatePetProfileRequest req = new CreatePetProfileRequest(
                "FluffyRenamed", "Cat", "Persian", LocalDate.now().minusYears(1),
                "Female", new BigDecimal("4.5"), "Adult", "Tuna", "None"
        );

        // Attempt to pass customerId=customerB.getId() in query param while updating petA
        mockMvc.perform(put("/api/v1/customer/pets/" + petA.getId() + "?customerId=" + customerB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify petA STILL belongs to customerA
        PetProfile updated = petProfileRepository.findById(petA.getId()).orElseThrow();
        assertEquals("FluffyRenamed", updated.getPetName());
        assertEquals(customerA.getId(), updated.getCustomer().getId(), "Pet ownership must never be altered");
    }

    // 10. Customer A can access their own pets
    @Test
    @WithMockUser(username = "customer.sec.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("Fix A.10: Authenticated Customer A can view their own pets and only their own pets")
    void testCustomerACanAccessOwnPets() throws Exception {
        mockMvc.perform(get("/api/v1/customer/pets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(petA.getId()))
                .andExpect(jsonPath("$.data[0].petName").value("Fluffy"));

        // Single pet detail for own pet
        mockMvc.perform(get("/api/v1/customer/pets/" + petA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.petName").value("Fluffy"));
    }

    // 11. Authorized admin access continues to work
    @Test
    @WithMockUser(username = "admin_sec_test", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("Fix A.11: Authorized admin can access admin pet endpoints")
    void testAdminAccessWorks() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));
    }

    // 12. Legitimate guest registration continues to work on dedicated endpoint
    @Test
    @DisplayName("Fix A.12: Unauthenticated guest can register pet on dedicated /guest endpoint")
    void testGuestRegistrationEndpointWorks() throws Exception {
        CreatePetProfileRequest req = new CreatePetProfileRequest(
                "GuestPuppy", "Dog", "Beagle", LocalDate.now().minusMonths(6),
                "Male", new BigDecimal("8.0"), "Puppy", "Puppy Chow", "None"
        );

        mockMvc.perform(post("/api/v1/customer/pets/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.petName").value("GuestPuppy"))
                .andExpect(jsonPath("$.data.petType").value("Dog"));
    }

    // 13. Guest registration cannot expose previously registered pets
    @Test
    @DisplayName("Fix A.13: Guest registration is strictly write-only and does not expose existing pets")
    void testGuestRegistrationCannotExposeExistingPets() throws Exception {
        // Attempting to list pets as guest must be rejected with 401
        mockMvc.perform(get("/api/v1/customer/pets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Attempting to get guest endpoint via GET must be 405 Method Not Allowed or 401
        mockMvc.perform(get("/api/v1/customer/pets/guest")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    // 14. A null customer context always fails ownership verification
    @Test
    @DisplayName("Fix A.14: isOwner(profile, null) must ALWAYS return false (Service-level invariance)")
    void testNullCustomerFailsOwnershipVerification() {
        assertFalse(petProfileService.isOwner(petA, null), "isOwner with null customer must return false");
        assertFalse(petProfileService.isOwner(null, customerA), "isOwner with null profile must return false");
        assertFalse(petProfileService.isOwner(null, null), "isOwner with null profile and null customer must return false");

        // Calling getPetProfileById with null customer must throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> petProfileService.getPetProfileById(petA.getId(), null));
        assertThrows(AccessDeniedException.class, () -> petProfileService.deletePetProfile(petA.getId(), null));
    }
}
