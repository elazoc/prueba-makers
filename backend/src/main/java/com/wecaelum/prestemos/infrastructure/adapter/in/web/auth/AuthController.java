package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.port.in.AuthenticateUseCase;
import com.wecaelum.prestemos.application.port.in.RegisterUserCommand;
import com.wecaelum.prestemos.application.port.in.RegisterUserUseCase;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.infrastructure.security.JwtProperties;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: log in with email and password, and self-register as a USER. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthenticateUseCase authenticate;
    private final RegisterUserUseCase registerUser;
    private final JwtProperties jwt;

    AuthController(AuthenticateUseCase authenticate, RegisterUserUseCase registerUser, JwtProperties jwt) {
        this.authenticate = authenticate;
        this.registerUser = registerUser;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authenticate.login(request.email(), request.password());
        return LoginResponse.bearer(
                result.token().value(), jwt.ttl().toSeconds(), UserResponse.from(result.user()));
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUser.register(
                new RegisterUserCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id().value()))
                .body(UserResponse.from(user));
    }
}
