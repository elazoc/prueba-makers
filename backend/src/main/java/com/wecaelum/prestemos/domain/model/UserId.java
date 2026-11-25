package com.wecaelum.prestemos.domain.model;

import java.io.Serializable;
import java.util.UUID;

public record UserId(UUID value) implements Serializable {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("El identificador es obligatorio.");
        }
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String raw) {
        try {
            return new UserId(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("El identificador no es válido.");
        }
    }
}
