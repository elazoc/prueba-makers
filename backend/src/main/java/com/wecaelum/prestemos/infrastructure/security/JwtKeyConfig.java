package com.wecaelum.prestemos.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256 keys and the JWT encoder and decoder built on them (design section 8). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    /** The RSA key pair that signs and verifies tokens. */
    record RsaKeys(RSAPublicKey publicKey, RSAPrivateKey privateKey) {

        @Override
        public String toString() {
            return "RsaKeys[publicKey=" + publicKey.getAlgorithm() + ", privateKey=[PROTECTED]]";
        }
    }

    @Bean
    RsaKeys jwtKeys(JwtProperties properties) {
        Resource publicPem = properties.publicKey();
        Resource privatePem = properties.privateKey();
        if (publicPem == null && privatePem == null) {
            log.warn("JWT keys generated at startup: tokens die on restart. Set security.jwt.* in production.");
            return generate();
        }
        if (publicPem == null || privatePem == null) {
            throw new IllegalStateException(
                    "Set both security.jwt.public-key and security.jwt.private-key, or neither.");
        }
        return new RsaKeys(read(publicPem, RsaKeyConverters.x509()), read(privatePem, RsaKeyConverters.pkcs8()));
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeys keys) {
        return NimbusJwtEncoder.withKeyPair(keys.publicKey(), keys.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeys keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static RsaKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RsaKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available in this JVM.", e);
        }
    }

    private static <K> K read(Resource pem, Converter<InputStream, K> converter) {
        try (InputStream in = pem.getInputStream()) {
            return converter.convert(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read JWT key " + pem.getDescription() + ".", e);
        }
    }
}
