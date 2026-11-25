package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserPersistenceAdapter.class)
class UserPersistenceAdapterH2Test {

    @Autowired
    UserRepository users;

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
    void savesUpdatesAndListsWithFilters() {
        User admin = users.findByEmail(new Email("admin@test.com")).orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);

        User saved = users.save(admin.changeProfile("Admin Renombrado", Role.ADMIN, true, admin.updatedAt().plusSeconds(5)));
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(users.findById(admin.id()).orElseThrow().fullName()).isEqualTo("Admin Renombrado");

        var admins = users.findAll(Role.ADMIN, true, new PageQuery(0, 10, "email", true));
        assertThat(admins.totalElements()).isEqualTo(1);
        assertThat(users.findAll(null, null, new PageQuery(0, 10, null, false)).totalElements()).isEqualTo(2);
        assertThat(users.findAll(null, false, new PageQuery(0, 10, "fullName", true)).totalElements()).isZero();
    }
}
