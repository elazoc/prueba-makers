package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers 401 as Problem Details: {@code ACCOUNT_DISABLED} when the token belongs to a deactivated user,
 * {@code UNAUTHENTICATED} otherwise (no token, bad signature, expired, unknown user).
 */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHENTICATED_DETAIL = "Inicia sesión para continuar.";

    private final ProblemResponseWriter writer;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean disabled = causedBy(ex, DisabledException.class);
        boolean badToken = disabled || causedBy(ex, OAuth2AuthenticationException.class);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        if (disabled) {
            DisabledAccountException account = new DisabledAccountException();
            writer.write(request, response, HttpStatus.UNAUTHORIZED, account.code(), account.getMessage());
        } else {
            writer.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", UNAUTHENTICATED_DETAIL);
        }
    }

    /** The resource server may hand the exception over directly or wrapped, so the whole cause chain is checked. */
    private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
