package com.wecaelum.prestemos.domain.model;

import java.io.Serializable;
import java.util.UUID;

public record LoanId(UUID value) implements Serializable {

    public LoanId {
        if (value == null) {
            throw new IllegalArgumentException("El identificador es obligatorio.");
        }
    }

    public static LoanId newId() {
        return new LoanId(UUID.randomUUID());
    }

    public static LoanId of(String raw) {
        try {
            return new LoanId(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("El identificador no es válido.");
        }
    }
}
