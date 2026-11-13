package com.fhir.examapp.service;

import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.dto.admin.AdminUserCreateRequest;
import com.fhir.examapp.dto.admin.AdminUserDTO;
import com.fhir.examapp.dto.admin.AdminUserMutationResponse;
import com.fhir.examapp.dto.admin.AdminUserUpdateRequest;
import com.fhir.examapp.repository.UserRepository;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    private static final String TEMP_PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    public AdminUserService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService,
                            SecurityContextHelper securityContextHelper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.securityContextHelper = securityContextHelper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUsers(String search, String roleStr, Boolean active, int page, int size) {
        Role roleFilter = null;
        if (roleStr != null && !roleStr.isBlank()) {
            try {
                roleFilter = Role.valueOf(roleStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<User> userPage = userRepository.searchUsers(
                search != null ? search.trim() : null,
                roleFilter,
                active,
                pageable
        );

        List<AdminUserDTO> dtos = userPage.getContent().stream()
                .map(AdminUserDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", dtos);
        result.put("page", userPage.getNumber());
        result.put("size", userPage.getSize());
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        return result;
    }

    public AdminUserMutationResponse createUser(AdminUserCreateRequest req, String ipAddress) {
        String username = req.getUsername() != null ? req.getUsername().trim() : "";
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String displayName = req.getDisplayName() != null ? req.getDisplayName().trim() : username;

        if (username.length() < 3) {
            throw new IllegalArgumentException("El nombre de usuario debe tener al menos 3 caracteres.");
        }
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("El email proporcionado no es válido.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con el username '" + username + "'.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el email '" + email + "'.");
        }

        Role role = Role.USER;
        if (req.getRole() != null && !req.getRole().isBlank()) {
            try {
                role = Role.valueOf(req.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Rol no válido: " + req.getRole());
            }
        }

        String rawPassword = req.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = generateTemporaryPassword(14);
        } else if (rawPassword.length() < 10) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 10 caracteres.");
        }

        User newUser = new User(
                username,
                email,
                displayName,
                passwordEncoder.encode(rawPassword),
                role
        );
        newUser.setActive(true);
        newUser.setMustChangePassword(true);
        newUser.setCreatedAt(User.formatNow());
        newUser.setUpdatedAt(User.formatNow());

        User saved = userRepository.save(newUser);

        User currentAdmin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(currentAdmin, "USER_CREATED", "USER", String.valueOf(saved.getId()),
                "Usuario creado: " + saved.getUsername() + " (rol=" + saved.getRole() + ")", ipAddress);

        return new AdminUserMutationResponse(
                AdminUserDTO.fromEntity(saved),
                rawPassword,
                "Usuario creado correctamente. Debe cambiar su contraseña en el primer inicio de sesión."
        );
    }

    public AdminUserDTO updateUser(Long targetUserId, AdminUserUpdateRequest req, String ipAddress) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + targetUserId));
        User currentAdmin = securityContextHelper.getCurrentUser();

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim().toLowerCase();
            Optional<User> existingByEmail = userRepository.findByEmailIgnoreCase(newEmail);
            if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(target.getId())) {
                throw new IllegalArgumentException("El email '" + newEmail + "' ya está en uso por otro usuario.");
            }
            target.setEmail(newEmail);
        }

        if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) {
            target.setDisplayName(req.getDisplayName().trim());
        }

        if (req.getRole() != null && !req.getRole().isBlank()) {
            Role newRole;
            try {
                newRole = Role.valueOf(req.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Rol no válido: " + req.getRole());
            }

            if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
                long activeAdmins = userRepository.countByRoleAndActiveTrue(Role.ADMIN);
                if (Boolean.TRUE.equals(target.getActive()) && activeAdmins <= 1) {
                    throw new IllegalStateException("No se puede quitar el rol ADMIN al último administrador activo del sistema.");
                }
                if (target.getId().equals(currentAdmin.getId()) && activeAdmins <= 1) {
                    throw new IllegalStateException("No puedes quitarte el rol de administrador siendo el único administrador activo.");
                }
            }
            target.setRole(newRole);
        }

        target.setUpdatedAt(User.formatNow());
        User saved = userRepository.save(target);

        auditService.logUserAction(currentAdmin, "USER_UPDATED", "USER", String.valueOf(saved.getId()),
                "Usuario actualizado: " + saved.getUsername() + " (rol=" + saved.getRole() + ", email=" + saved.getEmail() + ")",
                ipAddress);

        return AdminUserDTO.fromEntity(saved);
    }

    public AdminUserDTO updateUserStatus(Long targetUserId, boolean active, String ipAddress) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + targetUserId));
        User currentAdmin = securityContextHelper.getCurrentUser();

        if (!active) {
            if (target.getId().equals(currentAdmin.getId())) {
                throw new IllegalStateException("Un administrador no puede desactivarse a sí mismo.");
            }
            if (target.getRole() == Role.ADMIN && Boolean.TRUE.equals(target.getActive())) {
                long activeAdmins = userRepository.countByRoleAndActiveTrue(Role.ADMIN);
                if (activeAdmins <= 1) {
                    throw new IllegalStateException("No se puede desactivar al último administrador activo del sistema.");
                }
            }
        } else {
            target.setFailedLoginAttempts(0);
            target.setLockedUntil(null);
        }

        target.setActive(active);
        target.setUpdatedAt(User.formatNow());
        User saved = userRepository.save(target);

        auditService.logUserAction(currentAdmin, active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER", String.valueOf(saved.getId()),
                "Estado de usuario '" + saved.getUsername() + "' cambiado a active=" + active, ipAddress);

        return AdminUserDTO.fromEntity(saved);
    }

    public AdminUserMutationResponse resetPassword(Long targetUserId, String customPassword, String ipAddress) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + targetUserId));
        User currentAdmin = securityContextHelper.getCurrentUser();

        String tempPassword = customPassword;
        if (tempPassword == null || tempPassword.isBlank()) {
            tempPassword = generateTemporaryPassword(14);
        } else if (tempPassword.length() < 10) {
            throw new IllegalArgumentException("La contraseña temporal debe tener al menos 10 caracteres.");
        }

        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        target.setMustChangePassword(true);
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        target.setUpdatedAt(User.formatNow());
        User saved = userRepository.save(target);

        auditService.logUserAction(currentAdmin, "USER_PASSWORD_RESET", "USER", String.valueOf(saved.getId()),
                "Contraseña reseteada administrativamente para el usuario '" + saved.getUsername() + "'", ipAddress);

        return new AdminUserMutationResponse(
                AdminUserDTO.fromEntity(saved),
                tempPassword,
                "Contraseña reseteada. El usuario deberá cambiarla en su próximo inicio de sesión."
        );
    }

    private String generateTemporaryPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASS_CHARS.charAt(secureRandom.nextInt(TEMP_PASS_CHARS.length())));
        }
        return sb.toString();
    }
}
