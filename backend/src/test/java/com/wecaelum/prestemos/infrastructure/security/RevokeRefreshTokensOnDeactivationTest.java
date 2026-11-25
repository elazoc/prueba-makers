package com.wecaelum.prestemos.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.event.UserChanged;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeRefreshTokensOnDeactivationTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final UserId ID = UserId.newId();

    @Mock
    UserRepository users;

    @Mock
    RefreshTokenRepository refreshTokens;

    private RevokeRefreshTokensOnDeactivation listener() {
        return new RevokeRefreshTokensOnDeactivation(users, refreshTokens);
    }

    private static User user(boolean active) {
        return new User(ID, new Email("ana@test.com"), "HASH", "Ana Pérez", Role.USER, active, NOW, NOW, 1L);
    }

    @Test
    void deactivatedUserLosesAllRefreshTokens() {
        when(users.findById(ID)).thenReturn(Optional.of(user(false)));

        listener().onUserChanged(new UserChanged(ID, NOW));

        verify(refreshTokens).revokeAllForUser(ID, NOW);
    }

    @Test
    void activeUserKeepsTheirSessions() {
        when(users.findById(ID)).thenReturn(Optional.of(user(true)));

        listener().onUserChanged(new UserChanged(ID, NOW));

        verify(refreshTokens, never()).revokeAllForUser(any(), any());
    }
}
