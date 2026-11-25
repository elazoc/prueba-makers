package com.wecaelum.prestemos.domain.policy;

import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public record LoanPolicy(
        BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths, int maxPendingPerUser) {

    public void checkAmountAndTerm(Amount amount, LoanTerm term) {
        if (amount.value().compareTo(minAmount) < 0) {
            throw new LoanPolicyViolationException("El monto mínimo es $ " + money(minAmount) + ".");
        }
        if (amount.value().compareTo(maxAmount) > 0) {
            throw new LoanPolicyViolationException("El monto máximo es $ " + money(maxAmount) + ".");
        }
        if (term.months() < minTermMonths || term.months() > maxTermMonths) {
            throw new LoanPolicyViolationException(
                    "El plazo debe estar entre " + minTermMonths + " y " + maxTermMonths + " meses.");
        }
    }

    public void checkPendingCount(long currentPending) {
        if (currentPending >= maxPendingPerUser) {
            throw new LoanPolicyViolationException("Ya tienes " + maxPendingPerUser
                    + " préstamos pendientes. Espera a que se resuelva alguno para pedir otro.");
        }
    }

    private static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }
}
