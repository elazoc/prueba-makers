package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID applicantId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        int termMonths,
        LoanStatus status,
        Instant requestedAt,
        Instant decidedAt,
        String rejectionReason) {}
