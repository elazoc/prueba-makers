package com.wecaelum.prestemos.domain.event;

import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;

public record LoanRejected(
        UUID eventId, LoanId loanId, UserId applicantId, LoanStatus status, Instant occurredAt, String reason)
        implements LoanEvent {

    public static LoanRejected of(Loan loan, Instant now) {
        return new LoanRejected(
                UUID.randomUUID(), loan.id(), loan.applicantId(), loan.status(), now, loan.rejectionReason());
    }

    @Override
    public String type() {
        return "loan.rejected";
    }
}
