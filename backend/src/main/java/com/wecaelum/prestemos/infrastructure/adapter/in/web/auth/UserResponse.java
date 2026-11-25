package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Role role, boolean active, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id().value(), user.email().value(), user.fullName(), user.role(), user.active(), user.createdAt());
    }
}
