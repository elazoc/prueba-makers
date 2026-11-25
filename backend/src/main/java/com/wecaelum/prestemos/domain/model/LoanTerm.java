package com.wecaelum.prestemos.domain.model;

import java.io.Serializable;

public record LoanTerm(int months) implements Serializable {

    public LoanTerm {
        if (months < 1) {
            throw new IllegalArgumentException("El plazo debe ser de al menos 1 mes.");
        }
    }
}
