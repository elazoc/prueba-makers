package com.wecaelum.prestemos.domain.exception;

public class CannotDeactivateSelfException extends DomainException {

    public CannotDeactivateSelfException() {
        super("No puedes desactivar tu propia cuenta.");
    }

    @Override
    public String code() {
        return "CANNOT_DEACTIVATE_SELF";
    }
}
