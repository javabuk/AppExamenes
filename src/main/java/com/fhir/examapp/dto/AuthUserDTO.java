package com.fhir.examapp.dto;

import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;

public class AuthUserDTO {

    private Long id;
    private String username;
    private String email;
    private String displayName;
    private Role role;
    private Boolean active;
    private Boolean mustChangePassword;
    private String lastLoginAt;
    private String createdAt;

    public AuthUserDTO() {}

    public static AuthUserDTO fromEntity(User user) {
        if (user == null) return null;
        AuthUserDTO dto = new AuthUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setRole(user.getRole());
        dto.setActive(user.getActive());
        dto.setMustChangePassword(user.getMustChangePassword());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
