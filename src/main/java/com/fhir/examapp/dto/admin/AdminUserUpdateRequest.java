package com.fhir.examapp.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class AdminUserUpdateRequest {

    @Email(message = "El formato del email no es válido")
    private String email;

    @Size(max = 100, message = "El nombre visible no puede superar los 100 caracteres")
    private String displayName;

    private String role;

    public AdminUserUpdateRequest() {
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
