package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import java.io.Serializable;

/**
 * The caller of a request, as controllers receive it ({@code @AuthenticationPrincipal AuthenticatedUser me}).
 * The role comes from the database, not from the token.
 */
public record AuthenticatedUser(UserId id, Email email, Role role) implements Serializable {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
