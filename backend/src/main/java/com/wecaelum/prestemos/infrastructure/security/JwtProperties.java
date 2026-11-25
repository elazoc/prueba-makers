package com.wecaelum.prestemos.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * JWT settings ({@code security.jwt.*}). The PEM keys are optional: without both, {@link JwtKeyConfig}
 * generates a key pair at startup.
 */
@ConfigurationProperties("security.jwt")
public record JwtProperties(
        @DefaultValue("prestemos") String issuer,
        @DefaultValue("15m") Duration ttl,
        Resource publicKey,
        Resource privateKey) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer must not be blank.");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("security.jwt.ttl must be positive.");
        }
    }
}
