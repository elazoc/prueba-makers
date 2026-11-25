package com.wecaelum.prestemos.application.model;

import com.wecaelum.prestemos.domain.model.Loan;

public record LoanView(Loan loan, String applicantEmail, String applicantName) {}
