package com.wecaelum.prestemos.domain.event;

import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;

public sealed interface LoanEvent extends DomainEvent
        permits LoanRequested, LoanUpdated, LoanCancelled, LoanApproved, LoanRejected {

    UUID eventId();

    LoanId loanId();

    UserId applicantId();

    LoanStatus status();

    Instant occurredAt();

    String type();
}
