package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository repository;

    RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return RefreshTokenJpaMapper.toDomain(repository.saveAndFlush(RefreshTokenJpaMapper.toEntity(token)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String hash) {
        return repository.findByTokenHash(hash).map(RefreshTokenJpaMapper::toDomain);
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        repository.revokeFamily(familyId, now);
    }

    @Override
    public void revokeAllForUser(UserId userId, Instant now) {
        repository.revokeAllForUser(userId.value(), now);
    }
}
