package com.fhir.examapp.dto.admin;

public class AdminUserMutationResponse {
    private AdminUserDTO user;
    private String temporaryPassword;
    private String message;

    public AdminUserMutationResponse() {
    }

    public AdminUserMutationResponse(AdminUserDTO user, String temporaryPassword, String message) {
        this.user = user;
        this.temporaryPassword = temporaryPassword;
        this.message = message;
    }

    public AdminUserDTO getUser() { return user; }
    public void setUser(AdminUserDTO user) { this.user = user; }

    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
