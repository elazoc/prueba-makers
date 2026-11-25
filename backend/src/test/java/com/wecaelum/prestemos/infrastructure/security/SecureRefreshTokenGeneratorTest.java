package com.wecaelum.prestemos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.application.port.out.RefreshTokenGenerator.GeneratedToken;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRefreshTokenGeneratorTest {

    private final SecureRefreshTokenGenerator generator = new SecureRefreshTokenGenerator();

    @Test
    void generates32RandomBytesInUnpaddedBase64Url() {
        GeneratedToken token = generator.generate();

        assertThat(token.raw()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token.raw())).hasSize(32);
    }

    @Test
    void storesOnlyTheSha256HexOfTheRawValue() {
        GeneratedToken token = generator.generate();

        assertThat(token.hash()).matches("[0-9a-f]{64}").isEqualTo(generator.hash(token.raw()));
        assertThat(token.hash()).isNotEqualTo(token.raw());
        // SHA-256("abc"), FIPS 180-2 test vector.
        assertThat(generator.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(generator.generate().raw())).isTrue();
        }
    }
}
