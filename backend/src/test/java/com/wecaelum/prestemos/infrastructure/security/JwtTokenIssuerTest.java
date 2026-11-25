package com.wecaelum.prestemos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.model.IssuedToken;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtTokenIssuerTest {

    private final JwtProperties properties = new JwtProperties("prestemos", Duration.ofMinutes(60), null, null);
    private final JwtKeyConfig config = new JwtKeyConfig();
    private final JwtKeyConfig.RsaKeys keys = config.jwtKeys(properties);
    private final JwtEncoder encoder = config.jwtEncoder(keys);
    private final JwtDecoder decoder = config.jwtDecoder(keys, properties);
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(encoder, properties, Clock.systemUTC());

    private final User user =
            User.register(new Email("ana@test.com"), "{bcrypt}hash", "Ana Pérez", Role.USER, Instant.now());

    @Test
    void tokenHasClaimsAndExpiry() {
        IssuedToken token = issuer.issue(user);

        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo(user.id().value().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@test.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana Pérez");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("prestemos");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(3600));
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void decoderRejectsTokenFromAnotherIssuer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("otro")
                .subject(user.id().value().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void configuredPemKeysSignAndVerify() throws NoSuchAlgorithmException {
        KeyPair pair = rsaKeyPair();
        JwtProperties pem = new JwtProperties("prestemos", Duration.ofMinutes(60),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));

        JwtKeyConfig.RsaKeys loaded = config.jwtKeys(pem);

        assertThat(loaded.publicKey()).isEqualTo(pair.getPublic());
        assertThat(loaded.privateKey()).isEqualTo(pair.getPrivate());
        String token = new JwtTokenIssuer(config.jwtEncoder(loaded), pem, Clock.systemUTC()).issue(user).value();
        assertThat(config.jwtDecoder(loaded, pem).decode(token).getSubject()).isEqualTo(user.id().value().toString());
    }

    @Test
    void onlyOneConfiguredKeyFailsFast() throws NoSuchAlgorithmException {
        KeyPair pair = rsaKeyPair();
        JwtProperties halfConfigured = new JwtProperties("prestemos", Duration.ofMinutes(60),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()), null);

        assertThatThrownBy(() -> config.jwtKeys(halfConfigured)).isInstanceOf(IllegalStateException.class);
    }

    private static KeyPair rsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static ByteArrayResource pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        String text = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(text.getBytes(StandardCharsets.US_ASCII));
    }
}
