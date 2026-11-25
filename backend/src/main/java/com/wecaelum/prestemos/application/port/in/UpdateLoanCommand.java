package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.UserId;
import java.math.BigDecimal;

public record UpdateLoanCommand(LoanId loanId, UserId requesterId, BigDecimal amount, int termMonths) {}
