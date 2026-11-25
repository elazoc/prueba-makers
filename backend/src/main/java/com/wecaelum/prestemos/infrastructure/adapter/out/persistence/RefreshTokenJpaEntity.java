package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_expires_at", nullable = false)
    private Instant familyExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    protected RefreshTokenJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    UUID getFamilyId() {
        return familyId;
    }

    void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    Instant getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    void setFamilyExpiresAt(Instant familyExpiresAt) {
        this.familyExpiresAt = familyExpiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    UUID getReplacedById() {
        return replacedById;
    }

    void setReplacedById(UUID replacedById) {
        this.replacedById = replacedById;
    }
}
