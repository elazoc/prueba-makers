package com.wecaelum.prestemos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidRefreshTokenException;
import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.model.IssuedRefreshToken;
import com.wecaelum.prestemos.application.model.IssuedToken;
import com.wecaelum.prestemos.application.model.RefreshTokenPolicy;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.RefreshTokenGenerator;
import com.wecaelum.prestemos.application.port.out.RefreshTokenGenerator.GeneratedToken;
import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Login issuing the refresh token, rotation on refresh, reuse detection and logout ({@link AuthService}). */
@ExtendWith(MockitoExtension.class)
class RefreshSessionTest {

    private static final Instant LOGIN = Instant.parse("2026-11-25T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration GRACE = Duration.ofSeconds(10);
    private static final Email EMAIL = new Email("usuario@test.com");
    private static final UserId USER_ID = UserId.newId();
    private static final UUID FAMILY = UUID.randomUUID();

    @Mock
    UserRepository users;

    @Mock
    PasswordHasher hasher;

    @Mock
    TokenIssuer tokens;

    @Mock
    RefreshTokenRepository refreshTokens;

    @Mock
    RefreshTokenGenerator generator;

    private AuthService serviceAt(Instant now) {
        return new AuthService(users, hasher, tokens, refreshTokens, generator, new RefreshTokenPolicy(TTL, GRACE),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user(Role role, boolean active) {
        return new User(USER_ID, EMAIL, "HASH", "Usuario Demo", role, active, LOGIN, LOGIN, 1L);
    }

    private static RefreshToken activeToken(Instant createdAt) {
        return RefreshToken.issue(USER_ID, FAMILY, "h-old", LOGIN.plus(TTL), createdAt);
    }

    private void presented(RefreshToken token) {
        when(generator.hash("old")).thenReturn("h-old");
        when(refreshTokens.findByTokenHash("h-old")).thenReturn(Optional.of(token));
    }

    @Test
    void loginStartsANewFamilyThatLastsTheRefreshTtl() {
        User user = user(Role.USER, true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("123", "HASH")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new IssuedToken("jwt", LOGIN.plusSeconds(900)));
        when(generator.generate()).thenReturn(new GeneratedToken("raw", "h-raw"));

        AuthResult result = serviceAt(LOGIN).login("usuario@test.com", "123");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        RefreshToken token = saved.getValue();
        assertThat(token.userId()).isEqualTo(USER_ID);
        assertThat(token.tokenHash()).isEqualTo("h-raw");
        assertThat(token.familyExpiresAt()).isEqualTo(LOGIN.plus(TTL));
        assertThat(token.createdAt()).isEqualTo(LOGIN);
        assertThat(token.isRevoked()).isFalse();
        assertThat(result.refreshToken()).isEqualTo(new IssuedRefreshToken("raw", LOGIN.plus(TTL)));
        assertThat(result.token().value()).isEqualTo("jwt");
    }

    @Test
    void eachLoginStartsItsOwnFamily() {
        User user = user(Role.USER, true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("123", "HASH")).thenReturn(true);
        when(generator.generate()).thenReturn(new GeneratedToken("a", "h-a"), new GeneratedToken("b", "h-b"));

        serviceAt(LOGIN).login("usuario@test.com", "123");
        serviceAt(LOGIN).login("usuario@test.com", "123");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).familyId()).isNotEqualTo(saved.getAllValues().get(1).familyId());
    }

    @Test
    void refreshRotatesWithinTheSameFamilyAndKeepsItsAbsoluteEnd() {
        Instant now = LOGIN.plus(Duration.ofDays(3));
        RefreshToken current = activeToken(LOGIN);
        presented(current);
        User user = user(Role.USER, true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(generator.generate()).thenReturn(new GeneratedToken("new", "h-new"));
        IssuedToken access = new IssuedToken("jwt-2", now.plusSeconds(900));
        when(tokens.issue(user)).thenReturn(access);

        AuthResult result = serviceAt(now).refresh("old");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(saved.capture());
        List<RefreshToken> writes = saved.getAllValues();
        RefreshToken next = writes.stream().filter(t -> t.tokenHash().equals("h-new")).findFirst().orElseThrow();
        RefreshToken old = writes.stream().filter(t -> t.tokenHash().equals("h-old")).findFirst().orElseThrow();
        assertThat(next.familyId()).isEqualTo(FAMILY);
        assertThat(next.userId()).isEqualTo(USER_ID);
        assertThat(next.familyExpiresAt()).isEqualTo(LOGIN.plus(TTL));
        assertThat(next.createdAt()).isEqualTo(now);
        assertThat(next.isRevoked()).isFalse();
        assertThat(old.id()).isEqualTo(current.id());
        assertThat(old.revokedAt()).isEqualTo(now);
        assertThat(old.replacedById()).isEqualTo(next.id());
        assertThat(result.token()).isSameAs(access);
        assertThat(result.user()).isSameAs(user);
        assertThat(result.refreshToken()).isEqualTo(new IssuedRefreshToken("new", LOGIN.plus(TTL)));
        verify(refreshTokens, never()).revokeFamily(any(), any());
    }

    @Test
    void refreshIssuesTheAccessTokenWithTheCurrentRole() {
        presented(activeToken(LOGIN));
        User promoted = user(Role.ADMIN, true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(promoted));
        when(generator.generate()).thenReturn(new GeneratedToken("new", "h-new"));

        AuthResult result = serviceAt(LOGIN.plusSeconds(60)).refresh("old");

        verify(tokens).issue(promoted);
        assertThat(result.user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void reuseOutsideTheGraceRevokesTheWholeFamily() {
        Instant rotatedAt = LOGIN.plusSeconds(60);
        presented(activeToken(LOGIN).revokeReplacedBy(UUID.randomUUID(), rotatedAt));
        Instant now = rotatedAt.plus(GRACE);

        assertThatThrownBy(() -> serviceAt(now).refresh("old"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens).revokeFamily(FAMILY, now);
        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(tokens, users);
    }

    @Test
    void reuseInsideTheGraceIsRejectedWithoutRevokingTheFamily() {
        Instant rotatedAt = LOGIN.plusSeconds(60);
        presented(activeToken(LOGIN).revokeReplacedBy(UUID.randomUUID(), rotatedAt));

        assertThatThrownBy(() -> serviceAt(rotatedAt.plusSeconds(9)).refresh("old"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens, never()).revokeFamily(any(), any());
        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void aTokenRevokedByLogoutRevokesTheFamilyAgain() {
        presented(activeToken(LOGIN).revoke(LOGIN.plusSeconds(5)));
        Instant now = LOGIN.plusSeconds(6);

        assertThatThrownBy(() -> serviceAt(now).refresh("old")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens).revokeFamily(FAMILY, now);
    }

    @Test
    void theFamilyDiesSevenDaysAfterLoginEvenAfterRotations() {
        RefreshToken rotatedOnDaySix = RefreshToken.issue(
                USER_ID, FAMILY, "h-old", LOGIN.plus(TTL), LOGIN.plus(Duration.ofDays(6)));
        presented(rotatedOnDaySix);

        assertThatThrownBy(() -> serviceAt(LOGIN.plus(TTL)).refresh("old"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void inactiveUserCannotRefreshAndLosesAllSessions() {
        Instant now = LOGIN.plusSeconds(60);
        presented(activeToken(LOGIN));
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.USER, false)));

        assertThatThrownBy(() -> serviceAt(now).refresh("old"))
                .isInstanceOf(DisabledAccountException.class)
                .extracting(e -> ((DisabledAccountException) e).code())
                .isEqualTo("ACCOUNT_DISABLED");

        verify(refreshTokens).revokeAllForUser(USER_ID, now);
        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void unknownOrMissingTokenIsInvalid() {
        when(generator.hash("nadie")).thenReturn("h-nadie");
        when(refreshTokens.findByTokenHash("h-nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(LOGIN).refresh("nadie"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Tu sesión venció. Inicia sesión de nuevo.")
                .extracting(e -> ((InvalidRefreshTokenException) e).code())
                .isEqualTo("REFRESH_TOKEN_INVALID");
        assertThatThrownBy(() -> serviceAt(LOGIN).refresh(null)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> serviceAt(LOGIN).refresh("  ")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokens, never()).save(any());
        verify(refreshTokens, never()).revokeFamily(any(), any());
        verifyNoInteractions(tokens);
    }

    @Test
    void tokenOfADeletedUserIsInvalid() {
        presented(activeToken(LOGIN));
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(LOGIN.plusSeconds(1)).refresh("old"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void logoutRevokesTheFamilyOfThePresentedToken() {
        Instant now = LOGIN.plusSeconds(30);
        presented(activeToken(LOGIN));

        serviceAt(now).logout("old");

        verify(refreshTokens).revokeFamily(FAMILY, now);
    }

    @Test
    void logoutWithoutOrWithUnknownTokenDoesNothing() {
        when(generator.hash("nadie")).thenReturn("h-nadie");
        when(refreshTokens.findByTokenHash("h-nadie")).thenReturn(Optional.empty());

        serviceAt(LOGIN).logout(null);
        serviceAt(LOGIN).logout("");
        serviceAt(LOGIN).logout("nadie");

        verify(refreshTokens, never()).revokeFamily(any(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void issuedRefreshTokenHidesItsValue() {
        IssuedRefreshToken issued = new IssuedRefreshToken("secreto", LOGIN);
        GeneratedToken generated = new GeneratedToken("secreto", "hash");

        assertThat(issued.toString()).doesNotContain("secreto").contains("PROTECTED");
        assertThat(generated.toString()).doesNotContain("secreto").contains("PROTECTED");
    }
}
