package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Shared user cases run against H2 and against PostgreSQL. */
@Transactional
abstract class UserPersistenceAdapterContract {

    @Autowired
    UserRepository users;

    @Autowired
    EntityManager em;

    @Test
    void findByEmailIsExactOnNormalizedEmail() {
        assertThat(users.findByEmail(new Email("  USUARIO@Test.com ")))
                .get()
                .extracting(User::fullName)
                .isEqualTo("Usuario Demo");
        assertThat(users.findByEmail(new Email("usuario@test.co"))).isEmpty();
        assertThat(users.existsByEmail(new Email("admin@test.com"))).isTrue();
        assertThat(users.existsByEmail(new Email("nadie@test.com"))).isFalse();
    }

    @Test
    void seededPasswordsMatch123() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        for (String email : new String[] {"usuario@test.com", "admin@test.com"}) {
            User user = users.findByEmail(new Email(email)).orElseThrow();
            assertThat(encoder.matches("123", user.passwordHash())).as(email).isTrue();
        }
    }

    @Test
    void savingNewUserInsertsWithNullVersion() {
        Instant now = Instant.parse("2026-11-20T12:00:00.123456Z");
        User fresh = User.register(new Email("nuevo@test.com"), "{noop}x", "Nuevo Usuario", Role.USER, now);
        assertThat(fresh.version()).isNull();

        User saved = users.save(fresh);
        em.flush();
        em.clear();

        assertThat(saved.version()).isZero();
        User read = users.findById(fresh.id()).orElseThrow();
        assertThat(read.email().value()).isEqualTo("nuevo@test.com");
        assertThat(read.fullName()).isEqualTo("Nuevo Usuario");
        assertThat(read.role()).isEqualTo(Role.USER);
        assertThat(read.active()).isTrue();
        assertThat(read.createdAt()).isEqualTo(now);
        assertThat(read.version()).isZero();
    }

    @Test
    void savesUpdatesAndListsWithFilters() {
        User admin = users.findByEmail(new Email("admin@test.com")).orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);

        User saved = users.save(admin.changeProfile("Admin Renombrado", Role.ADMIN, true, admin.updatedAt().plusSeconds(5)));
        em.flush();
        em.clear();
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(users.findById(admin.id()).orElseThrow().fullName()).isEqualTo("Admin Renombrado");

        var admins = users.findAll(Role.ADMIN, true, new PageQuery(0, 10, "email", true));
        assertThat(admins.totalElements()).isEqualTo(1);
        assertThat(users.findAll(null, false, new PageQuery(0, 10, "fullName", true)).totalElements()).isZero();
    }

    @Test
    void searchWithNullRoleAndNullActiveReturnsEveryone() {
        var all = users.findAll(null, null, new PageQuery(0, 10, null, false));
        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(all.items()).extracting(u -> u.email().value())
                .containsExactlyInAnyOrder("usuario@test.com", "admin@test.com");
        assertThat(users.findAll(Role.USER, null, new PageQuery(0, 10, null, false)).totalElements()).isEqualTo(1);
        assertThat(users.findAll(null, true, new PageQuery(0, 10, null, false)).totalElements()).isEqualTo(2);
    }

    @Test
    void staleVersionFailsWithOptimisticLock() {
        User admin = users.findByEmail(new Email("admin@test.com")).orElseThrow();
        em.clear();
        Instant later = admin.updatedAt().plusSeconds(5);
        User first = admin.changeProfile("Primero", Role.ADMIN, true, later);
        User second = admin.changeProfile("Segundo", Role.ADMIN, true, later);

        User afterFirst = users.save(first);
        em.flush();
        em.clear();
        assertThat(afterFirst.version()).isEqualTo(1L);

        assertThatThrownBy(() -> {
                    users.save(second);
                    em.flush();
                })
                .isExactlyInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
