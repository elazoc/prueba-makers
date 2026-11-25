package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.Loan;

public interface RequestLoanUseCase {

    Loan request(RequestLoanCommand command);
}
