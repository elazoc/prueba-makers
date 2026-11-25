package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.UserId;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Shared refresh token cases run against H2 and against PostgreSQL, on the seeded users. */
@Transactional
abstract class RefreshTokenPersistenceAdapterContract {

    private static final UserId USER = UserId.of("00000000-0000-0000-0000-000000000001");
    private static final UserId ADMIN = UserId.of("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00.123456Z");
    private static final Instant FAMILY_END = NOW.plus(Duration.ofDays(7));

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    EntityManager em;

    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    private RefreshToken saved(RefreshToken token) {
        RefreshToken result = refreshTokens.save(token);
        em.flush();
        em.clear();
        return result;
    }

    @Test
    void savesAndFindsByHashWithEveryField() {
        UUID family = UUID.randomUUID();
        RefreshToken token = RefreshToken.issue(USER, family, hash('a'), FAMILY_END, NOW);

        assertThat(saved(token)).isEqualTo(token);

        assertThat(refreshTokens.findByTokenHash(hash('a'))).contains(token);
        assertThat(refreshTokens.findByTokenHash(hash('z'))).isEmpty();
    }

    @Test
    void savingARotationUpdatesTheExistingRow() {
        UUID family = UUID.randomUUID();
        RefreshToken first = saved(RefreshToken.issue(USER, family, hash('a'), FAMILY_END, NOW));
        RefreshToken second = saved(RefreshToken.issue(USER, family, hash('b'), FAMILY_END, NOW.plusSeconds(60)));

        saved(first.revokeReplacedBy(second.id(), NOW.plusSeconds(60)));

        RefreshToken read = refreshTokens.findByTokenHash(hash('a')).orElseThrow();
        assertThat(read.id()).isEqualTo(first.id());
        assertThat(read.revokedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(read.replacedById()).isEqualTo(second.id());
        assertThat(refreshTokens.findByTokenHash(hash('b')).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void tokenHashIsUnique() {
        saved(RefreshToken.issue(USER, UUID.randomUUID(), hash('a'), FAMILY_END, NOW));

        assertThatThrownBy(() -> refreshTokens.save(
                        RefreshToken.issue(ADMIN, UUID.randomUUID(), hash('a'), FAMILY_END, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokeFamilyRevokesOnlyItsLiveTokens() {
        UUID family = UUID.randomUUID();
        Instant earlier = NOW.plusSeconds(10);
        Instant later = NOW.plusSeconds(20);
        saved(RefreshToken.issue(USER, family, hash('a'), FAMILY_END, NOW).revoke(earlier));
        saved(RefreshToken.issue(USER, family, hash('b'), FAMILY_END, NOW));
        saved(RefreshToken.issue(USER, UUID.randomUUID(), hash('c'), FAMILY_END, NOW));

        refreshTokens.revokeFamily(family, later);

        assertThat(refreshTokens.findByTokenHash(hash('a')).orElseThrow().revokedAt()).isEqualTo(earlier);
        assertThat(refreshTokens.findByTokenHash(hash('b')).orElseThrow().revokedAt()).isEqualTo(later);
        assertThat(refreshTokens.findByTokenHash(hash('c')).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeAllForUserLeavesOtherUsersAlone() {
        Instant later = NOW.plusSeconds(20);
        saved(RefreshToken.issue(USER, UUID.randomUUID(), hash('a'), FAMILY_END, NOW));
        saved(RefreshToken.issue(USER, UUID.randomUUID(), hash('b'), FAMILY_END, NOW));
        saved(RefreshToken.issue(ADMIN, UUID.randomUUID(), hash('c'), FAMILY_END, NOW));

        refreshTokens.revokeAllForUser(USER, later);

        assertThat(refreshTokens.findByTokenHash(hash('a')).orElseThrow().revokedAt()).isEqualTo(later);
        assertThat(refreshTokens.findByTokenHash(hash('b')).orElseThrow().revokedAt()).isEqualTo(later);
        assertThat(refreshTokens.findByTokenHash(hash('c')).orElseThrow().isRevoked()).isFalse();
    }
}
