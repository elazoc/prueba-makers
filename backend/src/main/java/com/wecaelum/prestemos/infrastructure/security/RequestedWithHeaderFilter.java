package com.wecaelum.prestemos.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence for the endpoints that act on the refresh cookie: they require {@code X-Requested-With: prestemos}.
 * A form from another site cannot set headers, and a cross-origin {@code fetch} that does triggers a preflight
 * that CORS rejects. Together with {@code SameSite=Strict} and the cookie path, CSRF stays disabled elsewhere.
 * <p>
 * Built by {@link SecurityConfig}, not registered as a bean: Spring Boot would also add a {@code Filter} bean to
 * the servlet container, outside the security chain.
 */
class RequestedWithHeaderFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Requested-With";
    static final String VALUE = "prestemos";

    private static final RequestMatcher COOKIE_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"));

    private final ProblemResponseWriter writer;

    RequestedWithHeaderFilter(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !COOKIE_ENDPOINTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!VALUE.equals(request.getHeader(HEADER))) {
            writer.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Falta la cabecera X-Requested-With: prestemos. Envíala en esta solicitud.");
            return;
        }
        chain.doFilter(request, response);
    }
}
