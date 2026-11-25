package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.Loan;

public interface UpdateLoanUseCase {

    Loan update(UpdateLoanCommand command);
}
