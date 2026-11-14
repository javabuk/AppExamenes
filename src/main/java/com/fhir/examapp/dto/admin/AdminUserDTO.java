package com.fhir.examapp.dto.admin;

import com.fhir.examapp.domain.User;

public class AdminUserDTO {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private Boolean active;
    private Boolean mustChangePassword;
    private Integer failedLoginAttempts;
    private String lockedUntil;
    private String lastLoginAt;
    private String createdAt;
    private String updatedAt;
    private java.util.List<Long> assignedCertificationIds;
    private java.util.List<String> assignedCertificationCodes;

    public AdminUserDTO() {
    }

    public static AdminUserDTO fromEntity(User u) {
        if (u == null) return null;
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setDisplayName(u.getDisplayName());
        dto.setRole(u.getRole() != null ? u.getRole().name() : "USER");
        dto.setActive(u.getActive());
        dto.setMustChangePassword(u.getMustChangePassword());
        dto.setFailedLoginAttempts(u.getFailedLoginAttempts());
        dto.setLockedUntil(u.getLockedUntil());
        dto.setLastLoginAt(u.getLastLoginAt());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setUpdatedAt(u.getUpdatedAt());
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

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(Integer failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public String getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(String lockedUntil) { this.lockedUntil = lockedUntil; }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public java.util.List<Long> getAssignedCertificationIds() { return assignedCertificationIds; }
    public void setAssignedCertificationIds(java.util.List<Long> assignedCertificationIds) { this.assignedCertificationIds = assignedCertificationIds; }

    public java.util.List<String> getAssignedCertificationCodes() { return assignedCertificationCodes; }
    public void setAssignedCertificationCodes(java.util.List<String> assignedCertificationCodes) { this.assignedCertificationCodes = assignedCertificationCodes; }
}
