package com.wecaelum.prestemos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidCredentialsException;
import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.model.IssuedToken;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Email EMAIL = new Email("usuario@test.com");

    @Mock
    UserRepository repo;

    @Mock
    PasswordHasher hasher;

    @Mock
    TokenIssuer tokens;

    private AuthService service() {
        return new AuthService(repo, hasher, tokens);
    }

    private User user(boolean active) {
        return new User(UserId.newId(), EMAIL, "HASH", "Usuario Demo", Role.USER, active, NOW, NOW, 1L);
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailGivesSameError() {
        User user = user(true);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("mala", "HASH")).thenReturn(false);
        when(repo.findByEmail(new Email("nadie@test.com"))).thenReturn(Optional.empty());

        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(
                () -> service().login("usuario@test.com", "mala"));
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service().login("nadie@test.com", "mala"));

        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong.getMessage()).isEqualTo(unknown.getMessage()).isEqualTo("Email o contraseña incorrectos.");
        assertThat(((InvalidCredentialsException) wrong).code()).isEqualTo("INVALID_CREDENTIALS");
        verify(hasher).matches("mala", AuthService.DUMMY_HASH);
        verifyNoInteractions(tokens);
    }

    @Test
    void loginWithMalformedEmailIsInvalidCredentials() {
        assertThatThrownBy(() -> service().login("no-es-email", "123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos.");
        assertThatThrownBy(() -> service().login(null, "123")).isInstanceOf(InvalidCredentialsException.class);
        verify(repo, never()).findByEmail(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void loginOfInactiveUserIsRejected() {
        User user = user(false);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("123", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service().login("usuario@test.com", "123"))
                .isInstanceOf(DisabledAccountException.class)
                .hasMessage("Tu cuenta está desactivada. Escribe al administrador.")
                .extracting(e -> ((DisabledAccountException) e).code())
                .isEqualTo("ACCOUNT_DISABLED");
        verifyNoInteractions(tokens);
    }

    @Test
    void loginOfInactiveUserWithWrongPasswordDoesNotRevealDisabledAccount() {
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        when(hasher.matches("mala", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service().login("usuario@test.com", "mala"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginNormalizesEmail() {
        User user = user(true);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("123", "HASH")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new IssuedToken("jwt", NOW.plusSeconds(3600)));

        AuthResult result = service().login("  Usuario@Test.com ", "123");

        assertThat(result.user()).isSameAs(user);
    }

    @Test
    void loginReturnsIssuedToken() {
        User user = user(true);
        IssuedToken token = new IssuedToken("jwt", NOW.plusSeconds(3600));
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("123", "HASH")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(token);

        AuthResult result = service().login("usuario@test.com", "123");

        assertThat(result.token()).isSameAs(token);
        assertThat(result.user()).isSameAs(user);
    }
}
