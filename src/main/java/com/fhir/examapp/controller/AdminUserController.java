package com.fhir.examapp.controller;

import com.fhir.examapp.dto.admin.AdminUserCreateRequest;
import com.fhir.examapp.dto.admin.AdminUserDTO;
import com.fhir.examapp.dto.admin.AdminUserMutationResponse;
import com.fhir.examapp.dto.admin.AdminUserUpdateRequest;
import com.fhir.examapp.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.listUsers(search, role, active, page, size));
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody AdminUserCreateRequest request,
                                        HttpServletRequest httpRequest) {
        try {
            AdminUserMutationResponse response = adminUserService.createUser(request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody AdminUserUpdateRequest request,
                                        HttpServletRequest httpRequest) {
        try {
            AdminUserDTO updated = adminUserService.updateUser(id, request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id,
                                              @RequestBody Map<String, Object> payload,
                                              HttpServletRequest httpRequest) {
        try {
            Object rawActive = payload.get("active");
            if (rawActive == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El campo 'active' es obligatorio."));
            }
            boolean active = Boolean.parseBoolean(rawActive.toString());
            AdminUserDTO updated = adminUserService.updateUserStatus(id, active, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> payload,
                                           HttpServletRequest httpRequest) {
        try {
            String customPass = (payload != null && payload.get("newPassword") != null)
                    ? payload.get("newPassword").toString()
                    : null;
            AdminUserMutationResponse response = adminUserService.resetPassword(id, customPass, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
