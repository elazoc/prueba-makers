package com.wecaelum.prestemos.domain.exception;

public class LoanNotFoundException extends DomainException {

    public LoanNotFoundException() {
        super("No encontramos ese préstamo.");
    }

    @Override
    public String code() {
        return "LOAN_NOT_FOUND";
    }
}
