package com.wecaelum.prestemos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LoanQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final LoanPolicy POLICY =
            new LoanPolicy(new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3);

    @Mock
    LoanRepository repo;

    private final UserId owner = UserId.newId();
    private final Loan loan =
            Loan.request(owner, new Amount(new BigDecimal("1000")), new LoanTerm(12), POLICY, NOW);

    @Test
    void queryGetHidesForeignLoanFromUserButNotFromAdmin() {
        UserId stranger = UserId.newId();
        when(repo.findById(loan.id())).thenReturn(Optional.of(loan));
        LoanQueryService service = new LoanQueryService(repo);

        assertThat(service.get(loan.id(), owner, false)).isEqualTo(loan);
        assertThatThrownBy(() -> service.get(loan.id(), stranger, false)).isInstanceOf(LoanNotFoundException.class);
        assertThat(service.get(loan.id(), stranger, true)).isEqualTo(loan);
    }

    @Test
    void getMissingLoanThrowsNotFound() {
        LoanId id = LoanId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LoanQueryService(repo).get(id, owner, true))
                .isInstanceOf(LoanNotFoundException.class);
    }

    @Test
    void listMineAndListAllDelegateToRepository() {
        PageQuery q = new PageQuery(0, 10, "requestedAt", false);
        PageResult<Loan> mine = new PageResult<>(List.of(loan), 0, 10, 1);
        PageResult<LoanView> all = new PageResult<>(List.of(new LoanView(loan, "a@b.co", "Ana")), 0, 10, 1);
        Email email = new Email("a@b.co");
        when(repo.findByApplicant(owner, LoanStatus.PENDING, q)).thenReturn(mine);
        when(repo.findAll(null, email, q)).thenReturn(all);
        LoanQueryService service = new LoanQueryService(repo);

        assertThat(service.listMine(owner, LoanStatus.PENDING, q)).isSameAs(mine);
        assertThat(service.listAll(null, email, q)).isSameAs(all);
        verify(repo).findByApplicant(owner, LoanStatus.PENDING, q);
    }

    @Test
    void queryMethodsAreReadOnlyTransactional() {
        for (Method m : LoanQueryService.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                Transactional tx = m.getAnnotation(Transactional.class);
                assertThat(tx).as("method %s", m.getName()).isNotNull();
                assertThat(tx.readOnly()).isTrue();
            }
        }
    }
}
