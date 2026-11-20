package com.novapetzlife.customer;

import com.novapetzlife.auth.AuthResponse;
import com.novapetzlife.security.JwtTokenProvider;
import com.novapetzlife.user.Permission;
import com.novapetzlife.user.Role;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            UserRepository userRepository,
            JwtTokenProvider tokenProvider) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public Customer getOrCreateCustomerForUser(User user) {
        return customerRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Customer customer = new Customer(
                            user,
                            user.getEmail(),
                            user.getPhone() != null ? user.getPhone() : "",
                            user.getFirstName(),
                            user.getLastName(),
                            false
                    );
                    return customerRepository.save(customer);
                });
    }

    @Transactional
    public Customer createGuestCustomer(String email, String phone, String firstName, String lastName) {
        Customer guest = new Customer(
                null,
                email != null ? email.trim().toLowerCase() : null,
                phone != null ? phone.trim() : "",
                firstName != null ? firstName.trim() : "Guest",
                lastName != null ? lastName.trim() : "Customer",
                true
        );
        return customerRepository.save(guest);
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomerProfile(User user) {
        Customer customer = getOrCreateCustomerForUser(user);
        return CustomerDto.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public List<AddressDto> getCustomerAddresses(User user) {
        Customer customer = getOrCreateCustomerForUser(user);
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customer.getId()).stream()
                .map(AddressDto::fromEntity)
                .toList();
    }

    @Transactional
    public AddressDto addAddress(User user, AddressCreateRequest request) {
        Customer customer = getOrCreateCustomerForUser(user);

        if (Boolean.TRUE.equals(request.isDefault())) {
            List<CustomerAddress> existing = addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customer.getId());
            for (CustomerAddress addr : existing) {
                addr.setIsDefault(false);
                addressRepository.save(addr);
            }
        }

        CustomerAddress address = new CustomerAddress(
                customer,
                request.recipientName().trim(),
                request.phone().trim(),
                request.addressLine1().trim(),
                request.addressLine2(),
                request.landmark(),
                request.city().trim(),
                request.state().trim(),
                request.postalCode().trim(),
                request.latitude(),
                request.longitude(),
                request.isDefault()
        );

        CustomerAddress saved = addressRepository.save(address);
        return AddressDto.fromEntity(saved);
    }

    @Transactional
    public void deleteAddress(User user, Long addressId) {
        Customer customer = getOrCreateCustomerForUser(user);
        CustomerAddress address = addressRepository.findByIdAndCustomerId(addressId, customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Address not found or does not belong to user"));
        addressRepository.delete(address);
    }

    @Transactional
    public AuthResponse updateEmail(User user, String newEmail) {
        String trimmed = newEmail.trim().toLowerCase();
        if (user.getEmail().equalsIgnoreCase(trimmed)) {
            List<String> roles = user.getRoles().stream().map(Role::getName).toList();
            List<String> permissions = user.getPermissions() != null
                    ? user.getPermissions().stream().map(Permission::getName).toList()
                    : List.of();
            var auth = SecurityContextHolder.getContext().getAuthentication();
            String jwt = tokenProvider.generateToken(auth);
            return AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);
        }

        if (userRepository.existsByEmail(trimmed)) {
            throw new IllegalArgumentException("This email address is already in use by another account.");
        }

        user.setEmail(trimmed);
        userRepository.save(user);

        Customer customer = getOrCreateCustomerForUser(user);
        customer.setEmail(trimmed);
        customerRepository.save(customer);

        // Update Security Context and issue fresh JWT
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority(r.getName()))
                .toList();
        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                trimmed, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        String jwt = tokenProvider.generateToken(newAuth);

        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = user.getPermissions() != null
                ? user.getPermissions().stream().map(Permission::getName).toList()
                : List.of();
        return AuthResponse.of(jwt, user.getEmail(), user.getFirstName(), user.getLastName(), roles, permissions);
    }

    @Transactional
    public void deleteAccount(User user) {
        user.setIsActive(false);
        String deletedSuffix = "deleted_" + user.getId() + "_" + System.currentTimeMillis();
        user.setEmail(deletedSuffix + "@novapetzlife.deleted");
        user.setPhone(null);
        userRepository.save(user);

        customerRepository.findByUserId(user.getId()).ifPresent(customer -> {
            customer.setEmail(user.getEmail());
            customer.setPhone("");
            customerRepository.save(customer);
        });

        SecurityContextHolder.clearContext();
    }
}
