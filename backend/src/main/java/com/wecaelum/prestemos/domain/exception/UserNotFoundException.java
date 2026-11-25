package com.wecaelum.prestemos.domain.exception;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super("No encontramos ese usuario.");
    }

    @Override
    public String code() {
        return "USER_NOT_FOUND";
    }
}
