package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.Role;

public record CreateUserCommand(String email, String rawPassword, String fullName, Role role) {

    @Override
    public String toString() {
        return "CreateUserCommand[email=" + email + ", rawPassword=[PROTECTED], fullName=" + fullName
                + ", role=" + role + "]";
    }
}
