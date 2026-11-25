package com.wecaelum.prestemos.domain.exception;

public class CannotDemoteSelfException extends DomainException {

    public CannotDemoteSelfException() {
        super("No puedes quitarte el rol de administrador.");
    }

    @Override
    public String code() {
        return "CANNOT_DEMOTE_SELF";
    }
}
