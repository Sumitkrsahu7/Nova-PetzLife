package com.novapetzlife.user;

import com.novapetzlife.audit.AuditService;
import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('STAFF_MANAGE')")
@Tag(name = "Staff & Granular Role Management (Admin)", description = "Manage administrative staff accounts, modular permissions, and security credentials")
public class AdminStaffController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final com.novapetzlife.auth.RefreshTokenService refreshTokenService;

    public AdminStaffController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService,
            com.novapetzlife.auth.RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
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

    @GetMapping("/permissions")
    @Operation(summary = "List all available modular system permissions")
    public ResponseEntity<ApiResponse<List<Permission>>> getAllPermissions() {
        List<Permission> permissions = permissionRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create a new administrative staff account with granular permissions")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(
            @Valid @RequestBody CreateStaffRequest request,
            Authentication auth) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        // Enforce security rule: staff cannot create another Super Admin unless caller is Super Admin
        boolean callerIsSuperAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        String requestedRole = (request.role() != null && !request.role().isBlank())
                ? request.role().trim().toUpperCase()
                : "ROLE_STAFF_ADMIN";

        if ("ROLE_SUPER_ADMIN".equalsIgnoreCase(requestedRole) && !callerIsSuperAdmin) {
            throw new IllegalArgumentException("Unauthorized: Only Super Administrators can provision another Super Admin account.");
        }

        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + requestedRole));

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName() != null ? request.lastName().trim() : null,
                request.phone() != null ? request.phone().trim() : null
        );
        user.setRoles(Set.of(role));

        // Assign granular permissions if provided
        if (request.permissions() != null && !request.permissions().isEmpty()) {
            Set<Permission> assignedPermissions = permissionRepository.findByNameIn(request.permissions());
            user.setPermissions(assignedPermissions);
        } else {
            // Assign default operational staff permissions
            Set<Permission> defaultPerms = permissionRepository.findByNameIn(Set.of(
                    "ORDER_VIEW", "ORDER_EDIT", "PRODUCT_VIEW", "INVENTORY_VIEW", "CUSTOMER_SUPPORT_VIEW"
            ));
            user.setPermissions(defaultPerms);
        }

        User saved = userRepository.save(user);

        if (auditService != null) {
            auditService.recordAudit("User", saved.getEmail(), "STAFF_ACCOUNT_CREATED",
                    auth != null ? auth.getName() : "SUPER_ADMIN", null, role.getName(), null);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Staff account created successfully", StaffDto.fromEntity(saved)));
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Update staff profile information")
    public ResponseEntity<ApiResponse<StaffDto>> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStaffRequest request,
            Authentication auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName() != null ? request.lastName().trim() : "");
        user.setPhone(request.phone() != null ? request.phone().trim() : "");

        User saved = userRepository.save(user);

        if (auditService != null) {
            auditService.recordAudit("User", saved.getEmail(), "STAFF_PROFILE_UPDATED",
                    auth != null ? auth.getName() : "ADMIN", null, saved.getEmail(), null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Staff profile updated successfully", StaffDto.fromEntity(saved)));
    }

    @PutMapping("/{id}/permissions")
    @Transactional
    @Operation(summary = "Assign or update granular permissions for a staff member")
    public ResponseEntity<ApiResponse<StaffDto>> updateStaffPermissions(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStaffPermissionsRequest request,
            Authentication auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        // Prevent modifying permissions of the main Super Admin account accidentally
        boolean targetIsSuperAdmin = user.getRoles().stream()
                .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));

        if (targetIsSuperAdmin && auth != null && !auth.getName().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Cannot modify permissions for Super Administrator account.");
        }

        Set<Permission> updatedPerms = permissionRepository.findByNameIn(request.permissions());
        user.setPermissions(updatedPerms);
        User saved = userRepository.save(user);

        if (auditService != null) {
            auditService.recordAudit("User", saved.getEmail(), "STAFF_PERMISSIONS_UPDATED",
                    auth != null ? auth.getName() : "SUPER_ADMIN", null, String.join(",", request.permissions()), null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Staff permissions updated successfully", StaffDto.fromEntity(saved)));
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    @Operation(summary = "Securely reset staff credentials")
    public ResponseEntity<ApiResponse<Void>> resetStaffPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetStaffPasswordRequest request,
            Authentication auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Invalidate all existing sessions and refresh tokens on credential reset
        refreshTokenService.revokeAllUserTokens(user.getId(), "ADMIN_PASSWORD_RESET");

        if (auditService != null) {
            auditService.recordAudit("User", user.getEmail(), "STAFF_PASSWORD_RESET",
                    auth != null ? auth.getName() : "SUPER_ADMIN", null, "Credentials reset by administrator", null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Staff credentials reset successfully", null));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    @Operation(summary = "Toggle staff active status")
    public ResponseEntity<ApiResponse<StaffDto>> toggleStaffStatus(
            @PathVariable Long id,
            Authentication auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        // Safeguard: Do not deactivate the logged-in user or Super Admin if they are the only active Super Admin
        if (auth != null && auth.getName().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Cannot deactivate your own active session account.");
        }

        boolean next = !Boolean.TRUE.equals(user.getIsActive());
        user.setIsActive(next);
        User saved = userRepository.save(user);

        // If staff member was deactivated, immediately invalidate all their sessions
        if (!next) {
            refreshTokenService.revokeAllUserTokens(saved.getId(), "ADMIN_STAFF_DEACTIVATED");
        }

        if (auditService != null) {
            auditService.recordAudit("User", saved.getEmail(), "STAFF_STATUS_TOGGLED",
                    auth != null ? auth.getName() : "SUPER_ADMIN", String.valueOf(!next), String.valueOf(next), null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Staff status updated successfully", StaffDto.fromEntity(saved)));
    }
}
