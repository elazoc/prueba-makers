package com.wecaelum.prestemos.application.service;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.QueryLoansUseCase;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class LoanQueryService implements QueryLoansUseCase {

    private final LoanRepository loans;

    public LoanQueryService(LoanRepository loans) {
        this.loans = loans;
    }

    @Override
    @Transactional(readOnly = true)
    public Loan get(LoanId loanId, UserId requesterId, boolean requesterIsAdmin) {
        Loan loan = loans.findById(loanId).orElseThrow(LoanNotFoundException::new);
        if (!requesterIsAdmin && !loan.isOwnedBy(requesterId)) {
            throw new LoanNotFoundException();
        }
        return loan;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Loan> listMine(UserId applicantId, LoanStatus statusOrNull, PageQuery query) {
        return loans.findByApplicant(applicantId, statusOrNull, query);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LoanView> listAll(LoanStatus statusOrNull, Email applicantEmailOrNull, PageQuery query) {
        return loans.findAll(statusOrNull, applicantEmailOrNull, query);
    }
}
