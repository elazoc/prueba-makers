package com.wecaelum.prestemos.domain.event;

import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;

public record LoanApproved(UUID eventId, LoanId loanId, UserId applicantId, LoanStatus status, Instant occurredAt)
        implements LoanEvent {

    public static LoanApproved of(Loan loan, Instant now) {
        return new LoanApproved(UUID.randomUUID(), loan.id(), loan.applicantId(), loan.status(), now);
    }

    @Override
    public String type() {
        return "loan.approved";
    }
}
