package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.UserId;

public interface DecideLoanUseCase {

    Loan approve(LoanId loanId, UserId adminId);

    Loan reject(LoanId loanId, UserId adminId, String reason);
}
