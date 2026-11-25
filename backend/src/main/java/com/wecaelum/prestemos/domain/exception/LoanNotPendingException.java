package com.wecaelum.prestemos.domain.exception;

import com.wecaelum.prestemos.domain.model.LoanStatus;

public class LoanNotPendingException extends DomainException {

    public LoanNotPendingException(LoanStatus status) {
        super("Este préstamo ya está " + label(status) + ": solo se pueden cambiar préstamos pendientes.");
    }

    @Override
    public String code() {
        return "LOAN_NOT_PENDING";
    }

    private static String label(LoanStatus status) {
        return switch (status) {
            case PENDING -> "pendiente";
            case APPROVED -> "aprobado";
            case REJECTED -> "rechazado";
            case CANCELLED -> "cancelado";
        };
    }
}
