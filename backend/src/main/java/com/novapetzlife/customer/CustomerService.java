package com.novapetzlife.customer;

import com.novapetzlife.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerService(CustomerRepository customerRepository, CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
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
}
