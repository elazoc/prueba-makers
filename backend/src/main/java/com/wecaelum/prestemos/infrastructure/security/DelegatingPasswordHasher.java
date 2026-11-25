package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashes with Spring's {@link DelegatingPasswordEncoder} ({@code {bcrypt}} prefix). {@link #matches} never
 * throws: a malformed, unknown or prefix-less hash simply does not match.
 */
@Component
class DelegatingPasswordHasher implements PasswordHasher {

    /** bcrypt only reads the first 72 bytes of a password. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final PasswordEncoder encoder;

    DelegatingPasswordHasher() {
        DelegatingPasswordEncoder delegating =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        delegating.setDefaultPasswordEncoderForMatches(new NeverMatchingBcrypt());
        this.encoder = delegating;
    }

    @Override
    public String hash(String raw) {
        if (raw != null && raw.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña es muy larga: usa máximo 72 caracteres, menos si llevan tildes o emojis.");
        }
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        try {
            return encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Handles hashes without a known {@code {id}} prefix (like AuthService's dummy hash for unknown emails):
     * it still runs a bcrypt comparison so the response time does not reveal whether the email exists, but it
     * never reports a match.
     */
    private static final class NeverMatchingBcrypt implements PasswordEncoder {

        private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("Only used to compare hashes without a prefix.");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            try {
                bcrypt.matches(rawPassword, encodedPassword);
            } catch (IllegalArgumentException e) {
                // Not a bcrypt hash: nothing to burn, and it does not match either way.
            }
            return false;
        }
    }
}
