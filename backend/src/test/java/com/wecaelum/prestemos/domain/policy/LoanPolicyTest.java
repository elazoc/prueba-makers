package com.wecaelum.prestemos.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanPolicyTest {

    private final LoanPolicy policy =
            new LoanPolicy(new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3);

    private static Amount amount(String v) {
        return new Amount(new BigDecimal(v));
    }

    @Test
    void acceptsLimitsInclusive() {
        assertThatCode(() -> policy.checkAmountAndTerm(amount("100.00"), new LoanTerm(1))).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkAmountAndTerm(amount("50000000.00"), new LoanTerm(120)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> policy.checkAmountAndTerm(amount("99.99"), new LoanTerm(12)))
                .isInstanceOf(LoanPolicyViolationException.class)
                .hasMessage("El monto mínimo es $ 100,00.");
        assertThatThrownBy(() -> policy.checkAmountAndTerm(amount("50000000.01"), new LoanTerm(12)))
                .isInstanceOf(LoanPolicyViolationException.class)
                .hasMessage("El monto máximo es $ 50.000.000,00.");
        assertThatThrownBy(() -> policy.checkAmountAndTerm(amount("1000"), new LoanTerm(121)))
                .isInstanceOf(LoanPolicyViolationException.class)
                .hasMessage("El plazo debe estar entre 1 y 120 meses.");
        assertThatThrownBy(() -> policy.checkAmountAndTerm(amount("1000"), new LoanTerm(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFourthPending() {
        assertThatThrownBy(() -> policy.checkPendingCount(3))
                .isInstanceOf(LoanPolicyViolationException.class)
                .hasMessage("Ya tienes 3 préstamos pendientes. Espera a que se resuelva alguno para pedir otro.");
        assertThatCode(() -> policy.checkPendingCount(2)).doesNotThrowAnyException();
    }
}
