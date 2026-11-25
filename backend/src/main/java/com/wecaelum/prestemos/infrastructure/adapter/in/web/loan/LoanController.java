package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import com.wecaelum.prestemos.application.port.in.CancelLoanUseCase;
import com.wecaelum.prestemos.application.port.in.QueryLoansUseCase;
import com.wecaelum.prestemos.application.port.in.RequestLoanCommand;
import com.wecaelum.prestemos.application.port.in.RequestLoanUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateLoanCommand;
import com.wecaelum.prestemos.application.port.in.UpdateLoanUseCase;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageQueryFactory;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageResponse;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Loan endpoints for the person who asked for the loan; ownership is enforced by the use cases (404 on foreign ids). */
@RestController
@RequestMapping("/api/v1")
class LoanController {

    static final Set<String> SORT_FIELDS = Set.of("requestedAt", "amount", "status");
    static final String DEFAULT_SORT = "requestedAt,desc";

    private final RequestLoanUseCase requestLoan;
    private final UpdateLoanUseCase updateLoan;
    private final CancelLoanUseCase cancelLoan;
    private final QueryLoansUseCase queryLoans;

    LoanController(
            RequestLoanUseCase requestLoan,
            UpdateLoanUseCase updateLoan,
            CancelLoanUseCase cancelLoan,
            QueryLoansUseCase queryLoans) {
        this.requestLoan = requestLoan;
        this.updateLoan = updateLoan;
        this.cancelLoan = cancelLoan;
        this.queryLoans = queryLoans;
    }

    @PostMapping("/loans")
    ResponseEntity<LoanResponse> create(
            @AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody LoanRequest request) {
        Loan loan = requestLoan.request(new RequestLoanCommand(me.id(), request.amount(), request.termMonths()));
        return ResponseEntity.created(URI.create("/api/v1/loans/" + loan.id().value()))
                .body(LoanWebMapper.toResponse(loan));
    }

    @GetMapping("/me/loans")
    PageResponse<LoanResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var query = PageQueryFactory.of(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
        return PageResponse.from(queryLoans.listMine(me.id(), status, query), LoanWebMapper::toResponse);
    }

    @GetMapping("/loans/{id}")
    LoanResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return LoanWebMapper.toResponse(queryLoans.get(LoanId.of(id), me.id(), me.isAdmin()));
    }

    @PutMapping("/loans/{id}")
    LoanResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable String id,
            @Valid @RequestBody LoanRequest request) {
        Loan loan = updateLoan.update(
                new UpdateLoanCommand(LoanId.of(id), me.id(), request.amount(), request.termMonths()));
        return LoanWebMapper.toResponse(loan);
    }

    @DeleteMapping("/loans/{id}")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        cancelLoan.cancel(LoanId.of(id), me.id());
        return ResponseEntity.noContent().build();
    }
}
