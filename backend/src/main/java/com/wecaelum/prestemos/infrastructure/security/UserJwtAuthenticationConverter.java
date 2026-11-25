package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a valid JWT into an {@link AuthenticatedUser}. The role and the active flag are read from the database
 * on every request (through {@link UserAuthSnapshotProvider}), never trusted from the token.
 * <p>
 * Built by {@link SecurityConfig}, not registered as a bean: a {@code Converter} bean would also land in Spring
 * MVC's conversion service.
 */
class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAuthSnapshotProvider snapshots;

    UserJwtAuthenticationConverter(UserAuthSnapshotProvider snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserId id;
        Email email;
        try {
            id = UserId.of(jwt.getSubject());
            email = new Email(jwt.getClaimAsString("email"));
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("The token subject or email claim is malformed.");
        }
        UserAuthSnapshot snapshot = snapshots.find(id)
                .orElseThrow(() -> new InvalidBearerTokenException("The token subject is not a known user."));
        if (!snapshot.active()) {
            throw new DisabledException("The user account is disabled.");
        }
        AuthenticatedUser principal = new AuthenticatedUser(snapshot.id(), email, snapshot.role());
        return new AuthenticatedUserToken(principal, jwt);
    }

    /** Authentication whose principal is the {@link AuthenticatedUser} and whose credentials are the JWT. */
    static final class AuthenticatedUserToken extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;
        private final Jwt jwt;

        AuthenticatedUserToken(AuthenticatedUser principal, Jwt jwt) {
            super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            this.principal = principal;
            this.jwt = jwt;
            setAuthenticated(true);
        }

        @Override
        public AuthenticatedUser getPrincipal() {
            return principal;
        }

        @Override
        public Jwt getCredentials() {
            return jwt;
        }

        @Override
        public String getName() {
            return principal.id().value().toString();
        }
    }
}
