package com.novapetzlife.user;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
@Tag(name = "Staff & Role Management (Admin)", description = "Manage administrative staff accounts and roles")
public class AdminStaffController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminStaffController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Operation(summary = "List all administrative staff users")
    public ResponseEntity<ApiResponse<List<StaffDto>>> getStaffUsers() {
        List<User> users = userRepository.findAll();
        List<StaffDto> staffList = users.stream()
                .filter(u -> u.getRoles().stream().anyMatch(r ->
                        "ROLE_SUPER_ADMIN".equals(r.getName()) || "ROLE_STAFF_ADMIN".equals(r.getName())))
                .map(StaffDto::fromEntity)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(staffList));
    }

    @PostMapping
    @Operation(summary = "Create a new administrative staff account")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        String assignedRoleName = (request.role() != null && !request.role().isBlank())
                ? request.role().trim().toUpperCase()
                : "ROLE_STAFF_ADMIN";

        Role role = roleRepository.findByName(assignedRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + assignedRoleName));

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName() != null ? request.lastName().trim() : null,
                request.phone() != null ? request.phone().trim() : null
        );
        user.setRoles(Set.of(role));
        User saved = userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Staff account created successfully", StaffDto.fromEntity(saved)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle staff active status")
    public ResponseEntity<ApiResponse<StaffDto>> toggleStaffStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        boolean next = !Boolean.TRUE.equals(user.getIsActive());
        user.setIsActive(next);
        User saved = userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.ok("Staff account status updated", StaffDto.fromEntity(saved)));
    }
}
