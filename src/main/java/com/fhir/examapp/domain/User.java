package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private String lockedUntil;

    @Column(name = "last_login_at")
    private String lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    public User() {}

    public User(String username, String email, String displayName, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Role.USER;
        this.active = true;
        this.mustChangePassword = true;
        this.failedLoginAttempts = 0;
        this.createdAt = LocalDateTime.now().format(FORMATTER);
    }

    public static String formatNow() {
        return LocalDateTime.now().format(FORMATTER);
    }

    public static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            String normalized = raw.trim().replace(" ", "T");
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isCurrentlyLocked() {
        LocalDateTime until = getLockedUntilDateTime();
        return until != null && until.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(Integer failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public String getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(String lockedUntil) { this.lockedUntil = lockedUntil; }

    @JsonIgnore
    public LocalDateTime getLockedUntilDateTime() {
        return parseDateTime(lockedUntil);
    }

    public void setLockedUntilDateTime(LocalDateTime dt) {
        this.lockedUntil = dt != null ? dt.format(FORMATTER) : null;
    }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    @JsonIgnore
    public LocalDateTime getLastLoginDateTime() {
        return parseDateTime(lastLoginAt);
    }

    public void setLastLoginDateTime(LocalDateTime dt) {
        this.lastLoginAt = dt != null ? dt.format(FORMATTER) : null;
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
