package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import com.wecaelum.prestemos.application.port.in.DecideLoanUseCase;
import com.wecaelum.prestemos.application.port.in.QueryLoansUseCase;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageQueryFactory;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageResponse;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator endpoints: list every loan and decide pending ones. The role is checked by the security chain. */
@RestController
@RequestMapping("/api/v1/loans")
class AdminLoanController {

    private final QueryLoansUseCase queryLoans;
    private final DecideLoanUseCase decideLoan;

    AdminLoanController(QueryLoansUseCase queryLoans, DecideLoanUseCase decideLoan) {
        this.queryLoans = queryLoans;
        this.decideLoan = decideLoan;
    }

    @GetMapping
    PageResponse<AdminLoanResponse> list(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) String applicantEmail,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var query = PageQueryFactory.of(page, size, sort, LoanController.SORT_FIELDS, LoanController.DEFAULT_SORT);
        Email email = applicantEmail == null || applicantEmail.isBlank() ? null : new Email(applicantEmail);
        return PageResponse.from(queryLoans.listAll(status, email, query), LoanWebMapper::toAdminResponse);
    }

    @PostMapping("/{id}/approve")
    LoanResponse approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return LoanWebMapper.toResponse(decideLoan.approve(LoanId.of(id), me.id()));
    }

    @PostMapping("/{id}/reject")
    LoanResponse reject(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable String id,
            @Valid @RequestBody RejectRequest request) {
        return LoanWebMapper.toResponse(decideLoan.reject(LoanId.of(id), me.id(), request.reason()));
    }
}
