package com.wecaelum.prestemos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DelegatingPasswordHasherTest {

    /** Same value as AuthService.DUMMY_HASH: a bcrypt hash without the {bcrypt} prefix. */
    private static final String AUTH_SERVICE_DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa5ZpzO3Yv0x9r1W8s2m4kQd6uL1eVha";

    private final DelegatingPasswordHasher hasher = new DelegatingPasswordHasher();

    @Test
    void hashesWithBcryptPrefixAndMatches() {
        String hash = hasher.hash("secreto-largo");

        assertThat(hash).startsWith("{bcrypt}$2");
        assertThat(hasher.matches("secreto-largo", hash)).isTrue();
        assertThat(hasher.matches("otro-secreto", hash)).isFalse();
    }

    @Test
    void seededDemoHashMatches() {
        String seeded = "{bcrypt}$2a$10$DzQ3ZQ/uYbJGfFHwwUxmxuCVrxEdesjiadRWwNrN1MGZNj7J8iHYG";

        assertThat(hasher.matches("123", seeded)).isTrue();
    }

    @Test
    void dummyHashOfAuthServiceNeverMatches() {
        assertThat(hasher.matches("cualquier-cosa", AUTH_SERVICE_DUMMY_HASH)).isFalse();
    }

    @Test
    void prefixLessHashNeverMatchesEvenWithTheRightPassword() {
        String prefixed = hasher.hash("secreto-largo");
        String prefixLess = prefixed.substring("{bcrypt}".length());

        assertThat(hasher.matches("secreto-largo", prefixLess)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"no-es-un-hash", "{bcrypt}basura", "{desconocido}abc", "{bcrypt}", "{bcrypt"})
    void malformedOrUnsupportedHashesReturnFalseWithoutThrowing(String hash) {
        assertThat(hasher.matches("secreto-largo", hash)).isFalse();
    }

    @Test
    void passwordOverBcryptLimitDoesNotMatchAndCannotBeHashed() {
        String tooLong = "ñ".repeat(40); // 80 bytes in UTF-8, over the 72 bytes bcrypt uses
        String hash = hasher.hash("secreto-largo");

        assertThat(hasher.matches(tooLong, hash)).isFalse();
        assertThatThrownBy(() -> hasher.hash(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
    }
}
