package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "El motivo es obligatorio.")
        @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres.")
        String reason) {}
