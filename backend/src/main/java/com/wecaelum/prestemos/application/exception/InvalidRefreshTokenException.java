package com.wecaelum.prestemos.application.exception;

/** The refresh token is missing, unknown, expired or revoked; the client has to log in again. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Tu sesión venció. Inicia sesión de nuevo.");
    }

    public String code() {
        return "REFRESH_TOKEN_INVALID";
    }
}
