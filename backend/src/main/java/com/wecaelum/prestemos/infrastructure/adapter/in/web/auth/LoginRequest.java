package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

    public LoginRequest {
        // Trimmed before validation so " Ana@Mail.com " is accepted; the domain lower-cases it.
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[PROTECTED]]";
    }
}
