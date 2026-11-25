package com.wecaelum.prestemos.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoanEventTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final LoanPolicy POLICY =
            new LoanPolicy(new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3);

    private final UserId admin = UserId.newId();
    private final Loan loan =
            Loan.request(UserId.newId(), new Amount(new BigDecimal("1000")), new LoanTerm(12), POLICY, T0);

    @Test
    void eventTypesAreStable() {
        assertThat(LoanRequested.of(loan, T0).type()).isEqualTo("loan.requested");
        assertThat(LoanUpdated.of(loan, T1).type()).isEqualTo("loan.updated");
        assertThat(LoanCancelled.of(loan.cancel(), T1).type()).isEqualTo("loan.cancelled");
        assertThat(LoanApproved.of(loan.approve(admin, T1), T1).type()).isEqualTo("loan.approved");
        assertThat(LoanRejected.of(loan.reject(admin, "Ingresos insuficientes", T1), T1).type())
                .isEqualTo("loan.rejected");
    }

    @Test
    void eventsCarryLoanData() {
        Loan approved = loan.approve(admin, T1);
        LoanApproved e = LoanApproved.of(approved, T1);

        assertThat(e.loanId()).isEqualTo(loan.id());
        assertThat(e.applicantId()).isEqualTo(loan.applicantId());
        assertThat(e.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(e.occurredAt()).isEqualTo(T1);
        assertThat(e.eventId()).isNotNull().isNotEqualTo(LoanApproved.of(approved, T1).eventId());
    }

    @Test
    void rejectedCarriesReason() {
        Loan rejected = loan.reject(admin, "Ingresos insuficientes", T1);

        assertThat(LoanRejected.of(rejected, T1).reason()).isEqualTo("Ingresos insuficientes");
    }

    @Test
    void userChangedIsADomainEvent() {
        UserId id = UserId.newId();
        DomainEvent e = new UserChanged(id, T0);

        assertThat(e).isInstanceOf(UserChanged.class);
        assertThat(((UserChanged) e).userId()).isEqualTo(id);
    }
}
