package com.wecaelum.prestemos.infrastructure.adapter.in.web.user;

import com.wecaelum.prestemos.domain.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName, @NotNull Role role, @NotNull Boolean active) {}
