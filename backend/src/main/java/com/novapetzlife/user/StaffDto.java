package com.novapetzlife.user;

import java.time.Instant;
import java.util.List;

public record StaffDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Boolean isActive,
        List<String> roles,
        List<String> permissions,
        Instant createdAt
) {
    public static StaffDto fromEntity(User u) {
        List<String> roleNames = u.getRoles() != null
                ? u.getRoles().stream().map(Role::getName).toList()
                : List.of();
        List<String> permNames = u.getPermissions() != null
                ? u.getPermissions().stream().map(Permission::getName).toList()
                : List.of();
        return new StaffDto(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.getIsActive(),
                roleNames,
                permNames,
                u.getCreatedAt()
        );
    }
}
