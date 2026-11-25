package com.wecaelum.prestemos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant FAMILY_END = NOW.plus(Duration.ofDays(7));
    private static final UserId USER = UserId.newId();
    private static final UUID FAMILY = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    @Test
    void issueCreatesAnActiveTokenOfTheFamily() {
        RefreshToken token = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW);

        assertThat(token.id()).isNotNull();
        assertThat(token.userId()).isEqualTo(USER);
        assertThat(token.familyId()).isEqualTo(FAMILY);
        assertThat(token.tokenHash()).isEqualTo(HASH);
        assertThat(token.familyExpiresAt()).isEqualTo(FAMILY_END);
        assertThat(token.createdAt()).isEqualTo(NOW);
        assertThat(token.revokedAt()).isNull();
        assertThat(token.replacedById()).isNull();
        assertThat(token.isRevoked()).isFalse();
        assertThat(RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW).id()).isNotEqualTo(token.id());
    }

    @Test
    void revokeReplacedByRecordsTheSuccessorWithoutChangingTheOriginal() {
        RefreshToken token = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW);
        UUID successor = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);

        RefreshToken rotated = token.revokeReplacedBy(successor, later);

        assertThat(rotated.isRevoked()).isTrue();
        assertThat(rotated.revokedAt()).isEqualTo(later);
        assertThat(rotated.replacedById()).isEqualTo(successor);
        assertThat(rotated.id()).isEqualTo(token.id());
        assertThat(rotated.familyExpiresAt()).isEqualTo(FAMILY_END);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.replacedById()).isNull();
    }

    @Test
    void aRevokedTokenCannotBeRotatedAgain() {
        RefreshToken rotated = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW)
                .revokeReplacedBy(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> rotated.revokeReplacedBy(UUID.randomUUID(), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokeIsIdempotentAndKeepsTheFirstRevocation() {
        RefreshToken token = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW);

        RefreshToken revoked = token.revoke(NOW.plusSeconds(5));
        RefreshToken again = revoked.revoke(NOW.plusSeconds(50));

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(revoked.replacedById()).isNull();
        assertThat(again.revokedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void expiresAtTheEndOfTheFamilyLife() {
        RefreshToken token = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW);

        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(FAMILY_END.minusMillis(1))).isFalse();
        assertThat(token.isExpired(FAMILY_END)).isTrue();
        assertThat(token.isExpired(FAMILY_END.plusSeconds(1))).isTrue();
    }

    @Test
    void wasRotatedWithinOnlyCountsRecentRotations() {
        Duration grace = Duration.ofSeconds(10);
        RefreshToken token = RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, NOW);
        RefreshToken rotated = token.revokeReplacedBy(UUID.randomUUID(), NOW);
        RefreshToken revoked = token.revoke(NOW);

        assertThat(token.wasRotatedWithin(grace, NOW)).isFalse();
        assertThat(rotated.wasRotatedWithin(grace, NOW.plusSeconds(9))).isTrue();
        assertThat(rotated.wasRotatedWithin(grace, NOW.plusSeconds(10))).isFalse();
        assertThat(rotated.wasRotatedWithin(Duration.ZERO, NOW)).isFalse();
        assertThat(revoked.wasRotatedWithin(grace, NOW)).as("revoked by logout, not by rotation").isFalse();
    }

    @Test
    void requiredFieldsAreValidated() {
        assertThatThrownBy(() -> RefreshToken.issue(null, FAMILY, HASH, FAMILY_END, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(USER, null, HASH, FAMILY_END, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(USER, FAMILY, " ", FAMILY_END, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(USER, FAMILY, HASH, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(USER, FAMILY, HASH, FAMILY_END, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshToken(
                        UUID.randomUUID(), USER, FAMILY, HASH, FAMILY_END, NOW, null, UUID.randomUUID()))
                .as("a successor without a revocation date")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
