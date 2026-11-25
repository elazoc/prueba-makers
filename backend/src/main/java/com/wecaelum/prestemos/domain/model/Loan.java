package com.wecaelum.prestemos.domain.model;

import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.io.Serializable;
import java.time.Instant;

public record Loan(
        LoanId id,
        UserId applicantId,
        Amount amount,
        LoanTerm term,
        LoanStatus status,
        Instant requestedAt,
        Instant decidedAt,
        UserId decidedBy,
        String rejectionReason,
        Long version)
        implements Serializable {

    private static final int MIN_REASON = 5;
    private static final int MAX_REASON = 500;

    public Loan {
        require(id, "id");
        require(applicantId, "applicantId");
        require(amount, "amount");
        require(term, "term");
        require(status, "status");
        require(requestedAt, "requestedAt");
    }

    public static Loan request(UserId applicantId, Amount amount, LoanTerm term, LoanPolicy policy, Instant now) {
        policy.checkAmountAndTerm(amount, term);
        return new Loan(
                LoanId.newId(), applicantId, amount, term, LoanStatus.PENDING, now, null, null, null, null);
    }

    public Loan approve(UserId adminId, Instant now) {
        requirePending();
        return new Loan(
                id, applicantId, amount, term, LoanStatus.APPROVED, requestedAt, now, adminId, null, version);
    }

    public Loan reject(UserId adminId, String reason, Instant now) {
        requirePending();
        String clean = reason == null ? "" : reason.trim();
        if (clean.length() < MIN_REASON || clean.length() > MAX_REASON) {
            throw new IllegalArgumentException("El motivo debe tener entre 5 y 500 caracteres.");
        }
        return new Loan(
                id, applicantId, amount, term, LoanStatus.REJECTED, requestedAt, now, adminId, clean, version);
    }

    public Loan cancel() {
        requirePending();
        return new Loan(
                id, applicantId, amount, term, LoanStatus.CANCELLED, requestedAt, null, null, null, version);
    }

    public Loan update(Amount newAmount, LoanTerm newTerm, LoanPolicy policy) {
        requirePending();
        policy.checkAmountAndTerm(newAmount, newTerm);
        return new Loan(
                id, applicantId, newAmount, newTerm, status, requestedAt, decidedAt, decidedBy, rejectionReason, version);
    }

    public boolean isOwnedBy(UserId userId) {
        return applicantId.equals(userId);
    }

    private void requirePending() {
        if (status != LoanStatus.PENDING) {
            throw new LoanNotPendingException(status);
        }
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("El campo " + field + " del préstamo es obligatorio.");
        }
    }
}
