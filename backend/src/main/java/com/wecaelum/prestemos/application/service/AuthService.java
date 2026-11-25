package com.wecaelum.prestemos.application.service;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidCredentialsException;
import com.wecaelum.prestemos.application.exception.InvalidRefreshTokenException;
import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.model.IssuedRefreshToken;
import com.wecaelum.prestemos.application.model.RefreshTokenPolicy;
import com.wecaelum.prestemos.application.port.in.AuthenticateUseCase;
import com.wecaelum.prestemos.application.port.in.LogoutUseCase;
import com.wecaelum.prestemos.application.port.in.RefreshSessionUseCase;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.RefreshTokenGenerator;
import com.wecaelum.prestemos.application.port.out.RefreshTokenGenerator.GeneratedToken;
import com.wecaelum.prestemos.application.port.out.RefreshTokenRepository;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.RefreshToken;
import com.wecaelum.prestemos.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions: login, refresh-token rotation and logout. A session is a family of refresh tokens that lives
 * {@link RefreshTokenPolicy#ttl()} from login; each refresh replaces the presented token with a new one of the
 * same family. Presenting a token that was already rotated away is treated as theft and revokes the family,
 * unless it happened within {@link RefreshTokenPolicy#reuseGrace()} (two tabs racing).
 */
public class AuthService implements AuthenticateUseCase, RefreshSessionUseCase, LogoutUseCase {

    /** Not a real credential: only burns a hash comparison when the email is unknown. */
    static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa5ZpzO3Yv0x9r1W8s2m4kQd6uL1eVha";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenGenerator generator;
    private final RefreshTokenPolicy policy;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            PasswordHasher hasher,
            TokenIssuer tokens,
            RefreshTokenRepository refreshTokens,
            RefreshTokenGenerator generator,
            RefreshTokenPolicy policy,
            Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.policy = policy;
        this.clock = clock;
    }

    /** No transaction: its only write is one insert, and a transaction would hold a connection during bcrypt. */
    @Override
    public AuthResult login(String email, String rawPassword) {
        Optional<User> found = findUser(email);
        if (found.isEmpty()) {
            hasher.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!hasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.active()) {
            throw new DisabledAccountException();
        }
        Instant now = clock.instant();
        GeneratedToken generated = generator.generate();
        RefreshToken refresh = RefreshToken.issue(
                user.id(), UUID.randomUUID(), generated.hash(), now.plus(policy.ttl()), now);
        refreshTokens.save(refresh);
        return new AuthResult(tokens.issue(user), user, issued(generated, refresh));
    }

    /** The revocations made before rejecting must survive the rejection, hence {@code noRollbackFor}. */
    @Override
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, DisabledAccountException.class})
    public AuthResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(generator.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (current.isRevoked()) {
            if (!current.wasRotatedWithin(policy.reuseGrace(), now)) {
                refreshTokens.revokeFamily(current.familyId(), now);
            }
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(current.userId()).orElseThrow(InvalidRefreshTokenException::new);
        if (!user.active()) {
            refreshTokens.revokeAllForUser(user.id(), now);
            throw new DisabledAccountException();
        }
        GeneratedToken generated = generator.generate();
        RefreshToken next = RefreshToken.issue(
                user.id(), current.familyId(), generated.hash(), current.familyExpiresAt(), now);
        refreshTokens.save(next);
        refreshTokens.save(current.revokeReplacedBy(next.id(), now));
        return new AuthResult(tokens.issue(user), user, issued(generated, next));
    }

    @Override
    @Transactional
    public void logout(String rawTokenOrNull) {
        if (rawTokenOrNull == null || rawTokenOrNull.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(generator.hash(rawTokenOrNull))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
    }

    private static IssuedRefreshToken issued(GeneratedToken generated, RefreshToken token) {
        return new IssuedRefreshToken(generated.raw(), token.familyExpiresAt());
    }

    private Optional<User> findUser(String rawEmail) {
        Email email;
        try {
            email = new Email(rawEmail);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return users.findByEmail(email);
    }
}
