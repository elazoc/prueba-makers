package com.wecaelum.prestemos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void registerCreatesActiveUserWithNullVersion() {
        User user = User.register(new Email("a@b.co"), "hash", "Ana Pérez", Role.USER, T0);

        assertThat(user.id()).isNotNull();
        assertThat(user.active()).isTrue();
        assertThat(user.version()).isNull();
        assertThat(user.createdAt()).isEqualTo(T0);
        assertThat(user.updatedAt()).isEqualTo(T0);
        assertThat(user.role()).isEqualTo(Role.USER);
    }

    @Test
    void changeProfileUpdatesTimestampAndKeepsId() {
        User user = User.register(new Email("a@b.co"), "hash", "Ana Pérez", Role.USER, T0);

        User changed = user.changeProfile("Ana María", Role.ADMIN, false, T1);

        assertThat(changed.id()).isEqualTo(user.id());
        assertThat(changed.fullName()).isEqualTo("Ana María");
        assertThat(changed.role()).isEqualTo(Role.ADMIN);
        assertThat(changed.active()).isFalse();
        assertThat(changed.createdAt()).isEqualTo(T0);
        assertThat(changed.updatedAt()).isEqualTo(T1);
    }

    @Test
    void rejectsBlankOrTooLongName() {
        Email email = new Email("a@b.co");
        assertThatThrownBy(() -> User.register(email, "hash", " a ", Role.USER, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.register(email, "hash", "x".repeat(101), Role.USER, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
