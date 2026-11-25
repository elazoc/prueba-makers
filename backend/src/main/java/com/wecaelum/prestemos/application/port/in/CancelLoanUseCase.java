package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.UserId;

public interface CancelLoanUseCase {

    void cancel(LoanId loanId, UserId requesterId);
}
