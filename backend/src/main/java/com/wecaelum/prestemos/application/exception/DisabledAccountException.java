package com.wecaelum.prestemos.application.exception;

public class DisabledAccountException extends RuntimeException {

    public DisabledAccountException() {
        super("Tu cuenta está desactivada. Escribe al administrador.");
    }

    public String code() {
        return "ACCOUNT_DISABLED";
    }
}
