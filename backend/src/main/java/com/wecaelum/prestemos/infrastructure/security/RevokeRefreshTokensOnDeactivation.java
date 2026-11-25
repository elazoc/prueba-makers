package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.event.UserChanged;
import com.wecaelum.prestemos.domain.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends every session of a user who was just deactivated, in the same transaction as the change. The refresh
 * itself also rejects inactive users, so this only cleans up early; {@code UserService} knows nothing about it.
 */
@Component
class RevokeRefreshTokensOnDeactivation {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    RevokeRefreshTokensOnDeactivation(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onUserChanged(UserChanged event) {
        users.findById(event.userId())
                .filter(user -> !user.active())
                .map(User::id)
                .ifPresent(id -> refreshTokens.revokeAllForUser(id, event.occurredAt()));
    }
}
