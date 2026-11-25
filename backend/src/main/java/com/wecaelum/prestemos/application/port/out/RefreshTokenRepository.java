package com.wecaelum.prestemos.application.port.out;

import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    /** Locks the row for the rest of the transaction, so two refreshes of the same token run one after the other. */
    Optional<RefreshToken> findByTokenHash(String hash);

    /** Revokes every live token of the family; tokens already revoked keep their date. */
    void revokeFamily(UUID familyId, Instant now);

    /** Revokes every live token of the user, across families. */
    void revokeAllForUser(UserId userId, Instant now);
}
