package com.wecaelum.prestemos.domain.exception;

public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        super("Ya existe una cuenta con ese email.");
    }

    @Override
    public String code() {
        return "EMAIL_ALREADY_REGISTERED";
    }
}
