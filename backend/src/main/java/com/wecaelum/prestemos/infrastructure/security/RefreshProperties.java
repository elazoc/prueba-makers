package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.model.RefreshTokenPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Refresh token settings ({@code security.refresh.*}): life of a session family from login, grace for a token
 * just rotated away, and the name of the cookie that carries it.
 */
@ConfigurationProperties("security.refresh")
public record RefreshProperties(
        @DefaultValue("7d") Duration ttl,
        @DefaultValue("10s") Duration reuseGrace,
        @DefaultValue("prestemos_refresh") String cookieName) {

    public RefreshProperties {
        // RefreshTokenPolicy validates both durations.
        new RefreshTokenPolicy(ttl, reuseGrace);
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("security.refresh.cookie-name must not be blank.");
        }
    }

    public RefreshTokenPolicy toPolicy() {
        return new RefreshTokenPolicy(ttl, reuseGrace);
    }
}
