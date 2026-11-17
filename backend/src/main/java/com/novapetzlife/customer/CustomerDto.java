package com.novapetzlife.customer;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record CustomerDto(
        Long id,
        Long userId,
        String email,
        String phone,
        String firstName,
        String lastName,
        Boolean isGuest,
        List<AddressDto> addresses
) {
    public static CustomerDto fromEntity(Customer c) {
        List<AddressDto> addressDtos = c.getAddresses() != null
                ? c.getAddresses().stream().map(AddressDto::fromEntity).toList()
                : List.of();

        return new CustomerDto(
                c.getId(),
                c.getUser() != null ? c.getUser().getId() : null,
                c.getEmail(),
                c.getPhone(),
                c.getFirstName(),
                c.getLastName(),
                c.getIsGuest(),
                addressDtos
        );
    }
}
