package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;

public interface QueryLoansUseCase {

    Loan get(LoanId loanId, UserId requesterId, boolean requesterIsAdmin);

    PageResult<Loan> listMine(UserId applicantId, LoanStatus statusOrNull, PageQuery query);

    PageResult<LoanView> listAll(LoanStatus statusOrNull, Email applicantEmailOrNull, PageQuery query);
}
