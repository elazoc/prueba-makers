package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 100) String fullName) {

    public RegisterRequest {
        // Trimmed before validation, as the domain stores them.
        email = email == null ? null : email.strip();
        fullName = fullName == null ? null : fullName.strip();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=[PROTECTED], fullName=" + fullName + "]";
    }
}
