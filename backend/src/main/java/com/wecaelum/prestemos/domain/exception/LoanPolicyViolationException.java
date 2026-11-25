package com.wecaelum.prestemos.domain.exception;

public class LoanPolicyViolationException extends DomainException {

    public LoanPolicyViolationException(String detail) {
        super(detail);
    }

    @Override
    public String code() {
        return "LOAN_POLICY_VIOLATION";
    }
}
