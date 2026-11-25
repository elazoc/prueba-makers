package com.wecaelum.prestemos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalizesCaseAndSpaces() {
        assertThat(new Email("  Usuario@Test.com ").value()).isEqualTo("usuario@test.com");
    }

    @Test
    void rejectsMalformed() {
        for (String raw : new String[] {"no-arroba", "a@b", ""}) {
            assertThatThrownBy(() -> new Email(raw)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
