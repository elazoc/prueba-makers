package com.wecaelum.prestemos.application.service;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidCredentialsException;
import com.wecaelum.prestemos.application.model.AuthResult;
import com.wecaelum.prestemos.application.port.in.AuthenticateUseCase;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.User;
import java.util.Optional;

public class AuthService implements AuthenticateUseCase {

    /** Not a real credential: only burns a hash comparison when the email is unknown. */
    static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa5ZpzO3Yv0x9r1W8s2m4kQd6uL1eVha";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;

    public AuthService(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public AuthResult login(String email, String rawPassword) {
        Optional<User> found = findUser(email);
        if (found.isEmpty()) {
            hasher.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!hasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.active()) {
            throw new DisabledAccountException();
        }
        return new AuthResult(tokens.issue(user), user);
    }

    private Optional<User> findUser(String rawEmail) {
        Email email;
        try {
            email = new Email(rawEmail);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return users.findByEmail(email);
    }
}
