package com.wecaelum.prestemos.infrastructure.config;

import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Loan limits ({@code loans.policy.*}); {@link LoanPolicy} validates them when the context starts. */
@ConfigurationProperties("loans.policy")
public record LoanPolicyProperties(
        BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths, int maxPendingPerUser) {

    public LoanPolicy toPolicy() {
        return new LoanPolicy(minAmount, maxAmount, minTermMonths, maxTermMonths, maxPendingPerUser);
    }
}
