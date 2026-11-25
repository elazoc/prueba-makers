package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;

final class LoanJpaMapper {

    private LoanJpaMapper() {}

    static Loan toDomain(LoanJpaEntity e) {
        return new Loan(
                new LoanId(e.getId()), new UserId(e.getApplicantId()), new Amount(e.getAmount()),
                new LoanTerm(e.getTermMonths()), e.getStatus(), e.getRequestedAt(), e.getDecidedAt(),
                e.getDecidedBy() == null ? null : new UserId(e.getDecidedBy()), e.getRejectionReason(),
                e.getVersion());
    }

    static LoanJpaEntity toEntity(Loan l) {
        LoanJpaEntity e = new LoanJpaEntity();
        e.setId(l.id().value());
        e.setApplicantId(l.applicantId().value());
        e.setAmount(l.amount().value());
        e.setTermMonths(l.term().months());
        e.setStatus(l.status());
        e.setRequestedAt(l.requestedAt());
        e.setDecidedAt(l.decidedAt());
        e.setDecidedBy(l.decidedBy() == null ? null : l.decidedBy().value());
        e.setRejectionReason(l.rejectionReason());
        e.setVersion(l.version());
        return e;
    }
}
