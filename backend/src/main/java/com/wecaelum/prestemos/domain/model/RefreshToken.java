package com.wecaelum.prestemos.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One opaque refresh token of a session family. Only the hash of the value is kept. Every token of a family
 * shares {@code familyExpiresAt}, set at login, so rotating never extends the session. A token rotated away
 * keeps the id of its successor in {@code replacedById}; one revoked by logout or theft detection has none.
 */
public record RefreshToken(
        UUID id,
        UserId userId,
        UUID familyId,
        String tokenHash,
        Instant familyExpiresAt,
        Instant createdAt,
        Instant revokedAt,
        UUID replacedById) {

    public RefreshToken {
        require(id, "id");
        require(userId, "userId");
        require(familyId, "familyId");
        require(familyExpiresAt, "familyExpiresAt");
        require(createdAt, "createdAt");
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("El campo tokenHash del refresh token es obligatorio.");
        }
        if (replacedById != null && revokedAt == null) {
            throw new IllegalArgumentException("Un refresh token reemplazado debe tener fecha de revocación.");
        }
    }

    public static RefreshToken issue(
            UserId userId, UUID familyId, String tokenHash, Instant familyExpiresAt, Instant now) {
        return new RefreshToken(UUID.randomUUID(), userId, familyId, tokenHash, familyExpiresAt, now, null, null);
    }

    /** Revokes this token because {@code newId} took its place (rotation). */
    public RefreshToken revokeReplacedBy(UUID newId, Instant now) {
        if (isRevoked()) {
            throw new IllegalStateException("The refresh token " + id + " is already revoked.");
        }
        require(newId, "replacedById");
        require(now, "revokedAt");
        return new RefreshToken(id, userId, familyId, tokenHash, familyExpiresAt, createdAt, now, newId);
    }

    /** Revokes without a successor; revoking twice keeps the first date. */
    public RefreshToken revoke(Instant now) {
        if (isRevoked()) {
            return this;
        }
        require(now, "revokedAt");
        return new RefreshToken(id, userId, familyId, tokenHash, familyExpiresAt, createdAt, now, null);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(familyExpiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Whether this token was rotated away less than {@code grace} before {@code now}. */
    public boolean wasRotatedWithin(Duration grace, Instant now) {
        return replacedById != null && now.isBefore(revokedAt.plus(grace));
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("El campo " + field + " del refresh token es obligatorio.");
        }
    }
}
