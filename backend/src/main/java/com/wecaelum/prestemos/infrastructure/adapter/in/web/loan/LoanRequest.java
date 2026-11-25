package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Body of create and update. The amount accepts a JSON number or string; the policy checks the business range. */
public record LoanRequest(
        @NotNull(message = "El monto es obligatorio.")
        @Positive(message = "El monto debe ser mayor que cero.")
        @Digits(integer = 13, fraction = 2, message = "El monto admite hasta 13 enteros y 2 decimales.")
        BigDecimal amount,
        @NotNull(message = "El plazo es obligatorio.")
        @Positive(message = "El plazo debe ser mayor que cero.")
        Integer termMonths) {}
