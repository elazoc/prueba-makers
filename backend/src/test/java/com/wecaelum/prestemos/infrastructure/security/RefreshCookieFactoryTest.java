package com.wecaelum.prestemos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.application.model.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshCookieFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final RefreshCookieFactory cookies = new RefreshCookieFactory(
            new RefreshProperties(Duration.ofDays(7), Duration.ofSeconds(10), "prestemos_refresh"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sessionCookieIsHttpOnlySecureStrictAndScopedToAuth() {
        ResponseCookie cookie = cookies.issue(new IssuedRefreshToken("valor", NOW.plus(Duration.ofDays(2))));

        assertThat(cookie.getName()).isEqualTo("prestemos_refresh");
        assertThat(cookie.getValue()).isEqualTo("valor");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void maxAgeNeverGoesNegative() {
        ResponseCookie cookie = cookies.issue(new IssuedRefreshToken("valor", NOW.minusSeconds(5)));

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void clearingCookieExpiresAtOnceWithTheSameScope() {
        ResponseCookie cookie = cookies.clear();

        assertThat(cookie.getName()).isEqualTo("prestemos_refresh");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void propertiesDefaultToSevenDaysTenSecondsAndTheAgreedCookieName() {
        RefreshProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("security.refresh", RefreshProperties.class);

        assertThat(defaults.ttl()).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.reuseGrace()).isEqualTo(Duration.ofSeconds(10));
        assertThat(defaults.cookieName()).isEqualTo("prestemos_refresh");
        assertThat(defaults.toPolicy().ttl()).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.toPolicy().reuseGrace()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void readsTheTokenFromTheRequestCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(cookies.read(request)).isNull();

        request.setCookies(new Cookie("otra", "x"), new Cookie("prestemos_refresh", "valor"));
        assertThat(cookies.read(request)).isEqualTo("valor");
    }
}
