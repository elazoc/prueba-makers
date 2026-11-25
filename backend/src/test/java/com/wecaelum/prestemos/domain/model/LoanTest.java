package com.wecaelum.prestemos.domain.model;

import static com.wecaelum.prestemos.domain.model.LoanStatus.APPROVED;
import static com.wecaelum.prestemos.domain.model.LoanStatus.PENDING;
import static com.wecaelum.prestemos.domain.model.LoanStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoanTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final LoanPolicy POLICY =
            new LoanPolicy(new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3);

    private final UserId applicant = UserId.newId();
    private final UserId admin = UserId.newId();
    private final Loan pending = Loan.request(applicant, amount("1000"), new LoanTerm(12), POLICY, T0);

    private static Amount amount(String v) {
        return new Amount(new BigDecimal(v));
    }

    private Loan inStatus(LoanStatus status) {
        return new Loan(pending.id(), applicant, pending.amount(), pending.term(), status, T0, null, null, null, 5L);
    }

    @Test
    void requestStartsPending() {
        assertThat(pending.status()).isEqualTo(PENDING);
        assertThat(pending.id()).isNotNull();
        assertThat(pending.applicantId()).isEqualTo(applicant);
        assertThat(pending.requestedAt()).isEqualTo(T0);
        assertThat(pending.version()).isNull();
        assertThat(pending.decidedAt()).isNull();
        assertThat(pending.isOwnedBy(applicant)).isTrue();
        assertThat(pending.isOwnedBy(admin)).isFalse();
    }

    @Test
    void requestRejectsAmountOutsidePolicy() {
        assertThatThrownBy(() -> Loan.request(applicant, amount("99.99"), new LoanTerm(12), POLICY, T0))
                .isInstanceOf(LoanPolicyViolationException.class);
    }

    @Test
    void approveSetsDecisionFields() {
        Loan a = pending.approve(admin, T1);

        assertThat(a.status()).isEqualTo(APPROVED);
        assertThat(a.decidedBy()).isEqualTo(admin);
        assertThat(a.decidedAt()).isEqualTo(T1);
        assertThat(pending.status()).isEqualTo(PENDING);
    }

    @Test
    void rejectRequiresReason() {
        assertThatThrownBy(() -> pending.reject(admin, "   ", T1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(admin, "abc", T1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(admin, "x".repeat(501), T1))
                .isInstanceOf(IllegalArgumentException.class);

        Loan r = pending.reject(admin, "  Ingresos insuficientes ", T1);

        assertThat(r.status()).isEqualTo(REJECTED);
        assertThat(r.rejectionReason()).isEqualTo("Ingresos insuficientes");
        assertThat(r.decidedBy()).isEqualTo(admin);
        assertThat(r.decidedAt()).isEqualTo(T1);
    }

    @Test
    void cancelLeavesDecisionEmpty() {
        Loan c = pending.cancel();

        assertThat(c.status()).isEqualTo(LoanStatus.CANCELLED);
        assertThat(c.decidedAt()).isNull();
        assertThat(c.decidedBy()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = LoanStatus.class,
            names = {"APPROVED", "REJECTED", "CANCELLED"})
    void finalStatesCannotChange(LoanStatus s) {
        Loan loan = inStatus(s);
        assertThatThrownBy(() -> loan.approve(admin, T1)).isInstanceOf(LoanNotPendingException.class);
        assertThatThrownBy(() -> loan.reject(admin, "Motivo válido", T1)).isInstanceOf(LoanNotPendingException.class);
        assertThatThrownBy(loan::cancel).isInstanceOf(LoanNotPendingException.class);
        assertThatThrownBy(() -> loan.update(amount("2000"), new LoanTerm(6), POLICY))
                .isInstanceOf(LoanNotPendingException.class);
    }

    @Test
    void notPendingMessageUsesSpanishStatus() {
        assertThatThrownBy(() -> inStatus(APPROVED).cancel())
                .hasMessage("Este préstamo ya está aprobado: solo se pueden cambiar préstamos pendientes.")
                .extracting(e -> ((LoanNotPendingException) e).code())
                .isEqualTo("LOAN_NOT_PENDING");
        assertThatThrownBy(() -> inStatus(REJECTED).cancel()).hasMessageContaining("rechazado");
        assertThatThrownBy(() -> inStatus(LoanStatus.CANCELLED).cancel()).hasMessageContaining("cancelado");
    }

    @Test
    void updateRevalidatesPolicyAndKeepsRest() {
        Loan withVersion = inStatus(PENDING);
        Loan u = withVersion.update(amount("2000"), new LoanTerm(6), POLICY);

        assertThat(u.amount()).isEqualTo(amount("2000"));
        assertThat(u.term()).isEqualTo(new LoanTerm(6));
        assertThat(u.id()).isEqualTo(withVersion.id());
        assertThat(u.status()).isEqualTo(PENDING);
        assertThat(u.requestedAt()).isEqualTo(T0);
        assertThat(u.version()).isEqualTo(5L);
        assertThatThrownBy(() -> withVersion.update(amount("99"), new LoanTerm(6), POLICY))
                .isInstanceOf(LoanPolicyViolationException.class);
    }

    @Test
    void rejectsMissingMandatoryFields() {
        assertThatThrownBy(() -> new Loan(null, applicant, amount("1000"), new LoanTerm(1), PENDING, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Loan(LoanId.newId(), null, amount("1000"), new LoanTerm(1), PENDING, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Loan(LoanId.newId(), applicant, null, new LoanTerm(1), PENDING, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Loan(LoanId.newId(), applicant, amount("1000"), null, PENDING, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Loan(LoanId.newId(), applicant, amount("1000"), new LoanTerm(1), null, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Loan(LoanId.newId(), applicant, amount("1000"), new LoanTerm(1), PENDING, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loanIsSerializable() throws Exception {
        Loan loan = pending.reject(admin, "Ingresos insuficientes", T1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(loan);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(loan);
        }
    }
}
