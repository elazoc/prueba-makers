package com.wecaelum.prestemos.application.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos.");
    }

    public String code() {
        return "INVALID_CREDENTIALS";
    }
}
