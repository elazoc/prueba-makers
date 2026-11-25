package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.port.in.AuthenticateUseCase;
import com.wecaelum.prestemos.application.port.in.LogoutUseCase;
import com.wecaelum.prestemos.application.port.in.RefreshSessionUseCase;
import com.wecaelum.prestemos.application.port.in.RegisterUserCommand;
import com.wecaelum.prestemos.application.port.in.RegisterUserUseCase;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.infrastructure.security.JwtProperties;
import com.wecaelum.prestemos.infrastructure.security.RefreshCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints: log in with email and password, renew the session with the refresh cookie, log out, and
 * self-register as a USER. The refresh token only ever travels in the {@code HttpOnly} cookie, never in a body.
 * Refresh and logout require {@code X-Requested-With: prestemos}, checked by the security chain.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthenticateUseCase authenticate;
    private final RefreshSessionUseCase refreshSession;
    private final LogoutUseCase logout;
    private final RegisterUserUseCase registerUser;
    private final JwtProperties jwt;
    private final RefreshCookieFactory cookies;

    AuthController(
            AuthenticateUseCase authenticate,
            RefreshSessionUseCase refreshSession,
            LogoutUseCase logout,
            RegisterUserUseCase registerUser,
            JwtProperties jwt,
            RefreshCookieFactory cookies) {
        this.authenticate = authenticate;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.registerUser = registerUser;
        this.jwt = jwt;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return session(authenticate.login(request.email(), request.password()));
    }

    /**
     * A rejected refresh leaves the cookie alone: another tab may have just rotated it, and clearing it here would
     * wipe the cookie that tab received.
     */
    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        return session(refreshSession.refresh(cookies.read(request)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        logout.logout(cookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUser.register(
                new RegisterUserCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id().value()))
                .body(UserResponse.from(user));
    }

    private ResponseEntity<LoginResponse> session(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()).toString())
                .body(LoginResponse.bearer(
                        result.token().value(), jwt.ttl().toSeconds(), UserResponse.from(result.user())));
    }
}
