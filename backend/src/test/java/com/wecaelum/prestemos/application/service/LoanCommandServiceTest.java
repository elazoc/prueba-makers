package com.wecaelum.prestemos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.port.in.UpdateLoanCommand;
import com.wecaelum.prestemos.application.port.in.RequestLoanCommand;
import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.event.LoanCancelled;
import com.wecaelum.prestemos.domain.event.LoanRejected;
import com.wecaelum.prestemos.domain.event.LoanRequested;
import com.wecaelum.prestemos.domain.event.LoanUpdated;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LoanCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final LoanPolicy POLICY =
            new LoanPolicy(new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3);

    @Mock
    LoanRepository repo;

    @Mock
    DomainEventPublisher publisher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UserId applicant = UserId.newId();
    private final UserId admin = UserId.newId();

    private LoanCommandService service() {
        return new LoanCommandService(repo, publisher, POLICY, clock);
    }

    private Loan pending() {
        return Loan.request(applicant, new Amount(new BigDecimal("1000")), new LoanTerm(12), POLICY, NOW);
    }

    private Loan inStatus(LoanStatus status) {
        Loan p = pending();
        return new Loan(p.id(), applicant, p.amount(), p.term(), status, NOW, null, null, null, 1L);
    }

    @Test
    void requestSavesPendingAndPublishesLoanRequested() {
        when(repo.countByApplicantAndStatus(applicant, LoanStatus.PENDING)).thenReturn(0L);
        when(repo.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        Loan saved = service().request(new RequestLoanCommand(applicant, new BigDecimal("1000"), 12));

        assertThat(saved.status()).isEqualTo(LoanStatus.PENDING);
        assertThat(saved.requestedAt()).isEqualTo(NOW);
        verify(publisher)
                .publish(argThat(e -> e instanceof LoanRequested r && r.loanId().equals(saved.id())
                        && r.occurredAt().equals(NOW)));
    }

    @Test
    void requestFailsWhenThreePendingExist() {
        when(repo.countByApplicantAndStatus(applicant, LoanStatus.PENDING)).thenReturn(3L);

        assertThatThrownBy(() -> service().request(new RequestLoanCommand(applicant, new BigDecimal("1000"), 12)))
                .isInstanceOf(LoanPolicyViolationException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void publishesEventBuiltFromTheSavedLoan() {
        Loan loan = pending();
        Loan persisted = new Loan(
                loan.id(), applicant, loan.amount(), loan.term(), LoanStatus.APPROVED, NOW, NOW, admin, null, 9L);
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));
        when(repo.save(any(Loan.class))).thenReturn(persisted);

        Loan result = service().approve(loan.id(), admin);

        assertThat(result).isSameAs(persisted);
        verify(publisher).publish(argThat(e -> e instanceof LoanApproved a && a.status() == LoanStatus.APPROVED));
    }

    @Test
    void approveUsesUncachedReadAndPublishesLoanApproved() {
        Loan loan = pending();
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));
        when(repo.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        Loan result = service().approve(loan.id(), admin);

        assertThat(result.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(result.decidedBy()).isEqualTo(admin);
        assertThat(result.decidedAt()).isEqualTo(NOW);
        verify(repo).findByIdForUpdate(loan.id());
        verify(repo, never()).findById(any());
        verify(publisher).publish(argThat(e -> e instanceof LoanApproved a && a.loanId().equals(loan.id())));
    }

    @Test
    void rejectPublishesLoanRejectedWithReason() {
        Loan loan = pending();
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));
        when(repo.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        Loan result = service().reject(loan.id(), admin, "Ingresos insuficientes");

        assertThat(result.status()).isEqualTo(LoanStatus.REJECTED);
        verify(publisher)
                .publish(argThat(e -> e instanceof LoanRejected r && r.reason().equals("Ingresos insuficientes")));
    }

    @Test
    void deciding_missingLoan_throwsNotFound() {
        LoanId id = LoanId.newId();
        when(repo.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().approve(id, admin)).isInstanceOf(LoanNotFoundException.class);
        assertThatThrownBy(() -> service().reject(id, admin, "Motivo válido")).isInstanceOf(LoanNotFoundException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void updateOrCancelOfForeignLoan_throwsNotFound() {
        Loan loan = pending();
        UserId stranger = UserId.newId();
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service().update(new UpdateLoanCommand(loan.id(), stranger, new BigDecimal("500"), 6)))
                .isInstanceOf(LoanNotFoundException.class);
        assertThatThrownBy(() -> service().cancel(loan.id(), stranger)).isInstanceOf(LoanNotFoundException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void updatePublishesLoanUpdated() {
        Loan loan = pending();
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));
        when(repo.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        Loan result = service().update(new UpdateLoanCommand(loan.id(), applicant, new BigDecimal("500"), 6));

        assertThat(result.amount().value()).isEqualByComparingTo("500");
        assertThat(result.term().months()).isEqualTo(6);
        verify(publisher).publish(argThat(e -> e instanceof LoanUpdated u && u.loanId().equals(loan.id())));
    }

    @Test
    void cancelPublishesLoanCancelled() {
        Loan loan = pending();
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));
        when(repo.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        service().cancel(loan.id(), applicant);

        verify(repo).save(argThat(l -> l.status() == LoanStatus.CANCELLED));
        verify(publisher).publish(argThat(e -> e instanceof LoanCancelled c && c.loanId().equals(loan.id())));
    }

    @Test
    void nothingIsPublishedWhenDomainRejectsTransition() {
        Loan loan = inStatus(LoanStatus.APPROVED);
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service().approve(loan.id(), admin)).isInstanceOf(LoanNotPendingException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void updateOrCancelOfNonPendingLoanSavesAndPublishesNothing() {
        Loan loan = inStatus(LoanStatus.APPROVED);
        when(repo.findByIdForUpdate(loan.id())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service().update(new UpdateLoanCommand(loan.id(), applicant, new BigDecimal("500"), 6)))
                .isInstanceOf(LoanNotPendingException.class);
        assertThatThrownBy(() -> service().cancel(loan.id(), applicant)).isInstanceOf(LoanNotPendingException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void commandMethodsAreTransactional() {
        Method[] methods = LoanCommandService.class.getDeclaredMethods();
        int checked = 0;
        for (Method m : methods) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                assertThat(m.isAnnotationPresent(Transactional.class))
                        .as("method %s", m.getName())
                        .isTrue();
                checked++;
            }
        }
        assertThat(checked).isEqualTo(5);
    }
}
