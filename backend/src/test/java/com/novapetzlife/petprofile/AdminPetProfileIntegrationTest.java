package com.novapetzlife.petprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
import com.novapetzlife.petprofile.dto.UpdatePetFeedbackRequest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPetProfileIntegrationTest {

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

    private Customer customerA;
    private Customer customerB;
    private PetProfile petA1;
    private PetProfile petA2;
    private PetProfile petB1;

    @BeforeEach
    void setUp() {
        petProfileRepository.deleteAll();

        // Ensure roles exist
        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Store customer")));
        Role roleAdmin = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_SUPER_ADMIN", "Administrator")));

        // Create Customer A
        User userA = userRepository.findByEmail("pet.owner.a@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("pet.owner.a@example.com");
                    u.setPasswordHash("$2a$10$dummyHash");
                    u.setFirstName("Rahul");
                    u.setLastName("Nair");
                    u.setPhone("+919447111111");
                    u.setRoles(Set.of(roleCustomer));
                    return userRepository.save(u);
                });

        customerA = customerRepository.findByEmail("pet.owner.a@example.com")
                .orElseGet(() -> {
                    Customer c = new Customer(userA, "pet.owner.a@example.com", "+919447111111", "Rahul", "Nair", false);
                    return customerRepository.save(c);
                });

        // Create Customer B
        User userB = userRepository.findByEmail("pet.owner.b@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("pet.owner.b@example.com");
                    u.setPasswordHash("$2a$10$dummyHash");
                    u.setFirstName("Ananya");
                    u.setLastName("Menon");
                    u.setPhone("+919447222222");
                    u.setRoles(Set.of(roleCustomer));
                    return userRepository.save(u);
                });

        customerB = customerRepository.findByEmail("pet.owner.b@example.com")
                .orElseGet(() -> {
                    Customer c = new Customer(userB, "pet.owner.b@example.com", "+919447222222", "Ananya", "Menon", false);
                    return customerRepository.save(c);
                });

        // Pet A1: Dog
        petA1 = new PetProfile(customerA, "Bruno", "Dog", "Golden Retriever",
                LocalDate.of(2023, 1, 15), "Male", new BigDecimal("28.5"), "Adult",
                "Salmon kibble", "Chicken sensitivity");
        petA1 = petProfileRepository.save(petA1);

        // Pet A2: Cat
        petA2 = new PetProfile(customerA, "Milo", "Cat", "Persian",
                LocalDate.of(2022, 6, 10), "Male", new BigDecimal("4.2"), "Adult",
                "Tuna wet gravy", "None");
        petA2 = petProfileRepository.save(petA2);

        // Pet B1: Dog
        petB1 = new PetProfile(customerB, "Sheru", "Dog", "Indie",
                LocalDate.of(2021, 3, 20), "Male", new BigDecimal("18.0"), "Adult",
                "Rice and boiled veggies", "Corn allergy");
        petB1 = petProfileRepository.save(petB1);
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("1. Admin can list all registered pets with pagination, owner details, search, and species filter")
    void testAdminListPets() throws Exception {
        // Unfiltered list: 3 pets
        mockMvc.perform(get("/api/v1/admin/pets")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].ownerName").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].ownerPhone").isNotEmpty());

        // Species Filter: Cat -> only Milo
        mockMvc.perform(get("/api/v1/admin/pets")
                        .param("species", "Cat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].petName").value("Milo"))
                .andExpect(jsonPath("$.data.content[0].ownerName").value("Rahul Nair"));

        // Search by pet name: "Sheru"
        mockMvc.perform(get("/api/v1/admin/pets")
                        .param("query", "Sheru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].petName").value("Sheru"))
                .andExpect(jsonPath("$.data.content[0].ownerName").value("Ananya Menon"));

        // Search by owner phone: "9447111111"
        mockMvc.perform(get("/api/v1/admin/pets")
                        .param("query", "9447111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("2. Admin can retrieve individual pet details with complete owner contact and dietary profile")
    void testAdminGetPetById() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pets/" + petA1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.petName").value("Bruno"))
                .andExpect(jsonPath("$.data.petType").value("Dog"))
                .andExpect(jsonPath("$.data.breed").value("Golden Retriever"))
                .andExpect(jsonPath("$.data.ownerName").value("Rahul Nair"))
                .andExpect(jsonPath("$.data.ownerEmail").value("pet.owner.a@example.com"))
                .andExpect(jsonPath("$.data.ownerPhone").value("+919447111111"))
                .andExpect(jsonPath("$.data.dietaryPreferences").value("Salmon kibble"))
                .andExpect(jsonPath("$.data.foodSensitivities").value("Chicken sensitivity"))
                .andExpect(jsonPath("$.data.hasFeedback").value(false));
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("3. Admin can add, update, and remove feedback for a pet")
    void testAdminFeedbackCrud() throws Exception {
        // Add feedback
        UpdatePetFeedbackRequest req = new UpdatePetFeedbackRequest(
                "Based on Bruno's age and breed, you may explore our adult dog food collection. For specific dietary concerns, please consult your veterinarian."
        );

        mockMvc.perform(put("/api/v1/admin/pets/" + petA1.getId() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adminFeedback").value(containsString("Bruno's age and breed")))
                .andExpect(jsonPath("$.data.adminFeedbackUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.hasFeedback").value(true));

        // Verify DB update
        PetProfile updated = petProfileRepository.findById(petA1.getId()).orElseThrow();
        assertEquals(req.getFeedback(), updated.getAdminFeedback());
        assertNotNull(updated.getAdminFeedbackUpdatedAt());

        // Update feedback
        UpdatePetFeedbackRequest updateReq = new UpdatePetFeedbackRequest("Updated advice for Bruno: Switch to grain-free salmon formula.");
        mockMvc.perform(put("/api/v1/admin/pets/" + petA1.getId() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminFeedback").value("Updated advice for Bruno: Switch to grain-free salmon formula."));

        // Delete feedback via DELETE endpoint
        mockMvc.perform(delete("/api/v1/admin/pets/" + petA1.getId() + "/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        PetProfile cleared = petProfileRepository.findById(petA1.getId()).orElseThrow();
        assertNull(cleared.getAdminFeedback());
        assertNull(cleared.getAdminFeedbackUpdatedAt());

        // Blank/whitespace feedback via PUT also safely clears feedback
        petA1.setAdminFeedback("Temporary feedback");
        petProfileRepository.save(petA1);

        UpdatePetFeedbackRequest blankReq = new UpdatePetFeedbackRequest("   ");
        mockMvc.perform(put("/api/v1/admin/pets/" + petA1.getId() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminFeedback").doesNotExist())
                .andExpect(jsonPath("$.data.hasFeedback").value(false));
    }

    @Test
    @WithMockUser(username = "admin@novapetzlife.com", authorities = {"ROLE_SUPER_ADMIN"})
    @DisplayName("4. Feedback exceeding 2000 characters is rejected with validation error")
    void testFeedbackExceedingLimitRejected() throws Exception {
        String longText = "A".repeat(2001);
        UpdatePetFeedbackRequest req = new UpdatePetFeedbackRequest(longText);

        mockMvc.perform(put("/api/v1/admin/pets/" + petA1.getId() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "pet.owner.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("5. Customer pet GET responses include feedback fields, and customer cannot modify feedback")
    void testCustomerFeedbackVisibilityAndImmutability() throws Exception {
        // Admin publishes feedback for Bruno
        petA1.setAdminFeedback("Explore our adult dog food collection.");
        petProfileRepository.save(petA1);

        // Customer fetches their own pets
        mockMvc.perform(get("/api/v1/customer/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                // Bruno has feedback
                .andExpect(jsonPath("$.data[?(@.petName == 'Bruno')].adminFeedback").value(hasItem("Explore our adult dog food collection.")))
                // Milo does NOT have feedback
                .andExpect(jsonPath("$.data[?(@.petName == 'Milo')].adminFeedback").value(hasItem(nullValue())));

        // Customer updates pet details -> cannot set adminFeedback (CreatePetProfileRequest ignores/lacks it)
        CreatePetProfileRequest updateReq = new CreatePetProfileRequest(
                "Bruno", "Dog", "Golden Retriever", LocalDate.of(2023, 1, 15),
                "Male", new BigDecimal("29.0"), "Adult", "Salmon", "None"
        );

        mockMvc.perform(put("/api/v1/customer/pets/" + petA1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightKg").value(29.0))
                .andExpect(jsonPath("$.data.adminFeedback").value("Explore our adult dog food collection."));

        // Customer cannot access another customer's pet (403 Forbidden)
        mockMvc.perform(get("/api/v1/customer/pets/" + petB1.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pet.owner.a@example.com", authorities = {"ROLE_CUSTOMER"})
    @DisplayName("6. Customers cannot access admin pet endpoints (Forbidden)")
    void testCustomerCannotAccessAdminPetApis() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pets"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/pets/" + petA1.getId()))
                .andExpect(status().isForbidden());

        UpdatePetFeedbackRequest req = new UpdatePetFeedbackRequest("Malicious feedback");
        mockMvc.perform(put("/api/v1/admin/pets/" + petA1.getId() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/pets/" + petA1.getId() + "/feedback"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Unauthenticated users cannot access admin pet endpoints (Unauthorized / Forbidden)")
    void testUnauthenticatedCannotAccessAdminPetApis() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pets"))
                .andExpect(status().isUnauthorized());
    }
}
