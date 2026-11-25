package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.UserId;

final class RefreshTokenJpaMapper {

    private RefreshTokenJpaMapper() {}

    static RefreshToken toDomain(RefreshTokenJpaEntity e) {
        return new RefreshToken(
                e.getId(), new UserId(e.getUserId()), e.getFamilyId(), e.getTokenHash(), e.getFamilyExpiresAt(),
                e.getCreatedAt(), e.getRevokedAt(), e.getReplacedById());
    }

    static RefreshTokenJpaEntity toEntity(RefreshToken t) {
        RefreshTokenJpaEntity e = new RefreshTokenJpaEntity();
        e.setId(t.id());
        e.setUserId(t.userId().value());
        e.setFamilyId(t.familyId());
        e.setTokenHash(t.tokenHash());
        e.setFamilyExpiresAt(t.familyExpiresAt());
        e.setCreatedAt(t.createdAt());
        e.setRevokedAt(t.revokedAt());
        e.setReplacedById(t.replacedById());
        return e;
    }
}
