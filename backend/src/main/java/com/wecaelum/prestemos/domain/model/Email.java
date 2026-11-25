package com.wecaelum.prestemos.domain.model;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) implements Serializable {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
    }
}
