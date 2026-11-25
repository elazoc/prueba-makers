package com.wecaelum.prestemos.application.port.in;

public record RegisterUserCommand(String email, String rawPassword, String fullName) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", rawPassword=[PROTECTED], fullName=" + fullName + "]";
    }
}
