package com.wecaelum.prestemos.domain.model;

public enum LoanStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
