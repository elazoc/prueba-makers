package com.wecaelum.prestemos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountTest {

    @Test
    void normalizesToTwoDecimals() {
        assertThat(new Amount(new BigDecimal("1500")).value()).isEqualByComparingTo("1500.00").hasScaleOf(2);
    }

    @Test
    void rejectsZeroNegativeNullAndThreeDecimals() {
        for (String raw : new String[] {"0", "-1", "100.999"}) {
            assertThatThrownBy(() -> new Amount(new BigDecimal(raw))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Amount(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
