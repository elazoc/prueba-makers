package com.wecaelum.prestemos.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record Amount(BigDecimal value) implements Serializable {

    public Amount {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero y tener máximo 2 decimales.");
        }
        value = value.setScale(2);
    }
}
