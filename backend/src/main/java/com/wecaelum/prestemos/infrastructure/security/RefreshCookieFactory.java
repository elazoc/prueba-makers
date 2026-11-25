package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.model.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Writes, clears and reads the refresh token cookie: {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}
 * and limited to {@value #PATH}, so the browser only sends it to the session endpoints and scripts never see it.
 * Browsers accept {@code Secure} on {@code http://localhost}, so it works in development too.
 */
@Component
public class RefreshCookieFactory {

    public static final String PATH = "/api/v1/auth";

    private final String name;
    private final Clock clock;

    RefreshCookieFactory(RefreshProperties properties, Clock clock) {
        this.name = properties.cookieName();
        this.clock = clock;
    }

    /** The cookie lives until the end of the session family, not a fresh {@code ttl} per rotation. */
    public ResponseCookie issue(IssuedRefreshToken token) {
        Duration left = Duration.between(clock.instant(), token.expiresAt());
        return base(token.value()).maxAge(left.isNegative() ? Duration.ZERO : left).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** The raw token, or {@code null} when the request carries no cookie. */
    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(true).sameSite("Strict").path(PATH);
    }
}
