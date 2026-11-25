package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.domain.model.Loan;

final class LoanWebMapper {

    private LoanWebMapper() {}

    static LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.id().value(),
                loan.applicantId().value(),
                loan.amount().value(),
                loan.term().months(),
                loan.status(),
                loan.requestedAt(),
                loan.decidedAt(),
                loan.rejectionReason());
    }

    static AdminLoanResponse toAdminResponse(LoanView view) {
        Loan loan = view.loan();
        return new AdminLoanResponse(
                loan.id().value(),
                loan.applicantId().value(),
                loan.amount().value(),
                loan.term().months(),
                loan.status(),
                loan.requestedAt(),
                loan.decidedAt(),
                loan.rejectionReason(),
                view.applicantEmail(),
                view.applicantName());
    }
}
