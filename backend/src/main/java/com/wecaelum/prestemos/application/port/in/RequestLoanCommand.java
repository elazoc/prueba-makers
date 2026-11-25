package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.UserId;
import java.math.BigDecimal;

public record RequestLoanCommand(UserId applicantId, BigDecimal amount, int termMonths) {}
