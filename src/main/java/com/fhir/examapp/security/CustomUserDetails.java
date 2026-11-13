package com.fhir.examapp.security;

import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;
    private final boolean locked;
    private final boolean mustChangePassword;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole() != null ? user.getRole() : Role.USER;
        this.active = Boolean.TRUE.equals(user.getActive());
        this.locked = user.isCurrentlyLocked();
        this.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
