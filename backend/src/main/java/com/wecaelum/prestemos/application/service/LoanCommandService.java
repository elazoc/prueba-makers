package com.wecaelum.prestemos.application.service;

import com.wecaelum.prestemos.application.port.in.CancelLoanUseCase;
import com.wecaelum.prestemos.application.port.in.DecideLoanUseCase;
import com.wecaelum.prestemos.application.port.in.RequestLoanCommand;
import com.wecaelum.prestemos.application.port.in.RequestLoanUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateLoanCommand;
import com.wecaelum.prestemos.application.port.in.UpdateLoanUseCase;
import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.event.LoanCancelled;
import com.wecaelum.prestemos.domain.event.LoanRejected;
import com.wecaelum.prestemos.domain.event.LoanRequested;
import com.wecaelum.prestemos.domain.event.LoanUpdated;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public class LoanCommandService
        implements RequestLoanUseCase, UpdateLoanUseCase, CancelLoanUseCase, DecideLoanUseCase {

    private final LoanRepository loans;
    private final DomainEventPublisher events;
    private final LoanPolicy policy;
    private final Clock clock;

    public LoanCommandService(LoanRepository loans, DomainEventPublisher events, LoanPolicy policy, Clock clock) {
        this.loans = loans;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Loan request(RequestLoanCommand command) {
        policy.checkPendingCount(loans.countByApplicantAndStatus(command.applicantId(), LoanStatus.PENDING));
        Instant now = clock.instant();
        Loan loan = Loan.request(
                command.applicantId(),
                new Amount(command.amount()),
                new LoanTerm(command.termMonths()),
                policy,
                now);
        Loan saved = loans.save(loan);
        events.publish(LoanRequested.of(saved, now));
        return saved;
    }

    @Override
    @Transactional
    public Loan update(UpdateLoanCommand command) {
        Loan loan = findOwned(command.loanId(), command.requesterId());
        Loan updated = loan.update(new Amount(command.amount()), new LoanTerm(command.termMonths()), policy);
        Loan saved = loans.save(updated);
        events.publish(LoanUpdated.of(saved, clock.instant()));
        return saved;
    }

    @Override
    @Transactional
    public void cancel(LoanId loanId, UserId requesterId) {
        Loan saved = loans.save(findOwned(loanId, requesterId).cancel());
        events.publish(LoanCancelled.of(saved, clock.instant()));
    }

    @Override
    @Transactional
    public Loan approve(LoanId loanId, UserId adminId) {
        Instant now = clock.instant();
        Loan saved = loans.save(find(loanId).approve(adminId, now));
        events.publish(LoanApproved.of(saved, now));
        return saved;
    }

    @Override
    @Transactional
    public Loan reject(LoanId loanId, UserId adminId, String reason) {
        Instant now = clock.instant();
        Loan saved = loans.save(find(loanId).reject(adminId, reason, now));
        events.publish(LoanRejected.of(saved, now));
        return saved;
    }

    private Loan find(LoanId loanId) {
        return loans.findByIdForUpdate(loanId).orElseThrow(LoanNotFoundException::new);
    }

    private Loan findOwned(LoanId loanId, UserId requesterId) {
        Loan loan = find(loanId);
        if (!loan.isOwnedBy(requesterId)) {
            throw new LoanNotFoundException();
        }
        return loan;
    }
}
