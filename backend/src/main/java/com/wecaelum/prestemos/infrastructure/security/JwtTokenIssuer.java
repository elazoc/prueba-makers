package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.model.IssuedToken;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Signs RS256 access tokens with the claims {@code sub}, {@code email}, {@code name} and {@code roles}. */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user) {
        // JWT dates have second precision: truncating keeps expiresAt equal to the exp claim.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email().value())
                .claim("name", user.fullName())
                .claim("roles", List.of(user.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
