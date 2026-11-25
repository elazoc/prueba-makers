package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/** Shared cases run against H2 and against PostgreSQL. */
@Transactional
abstract class LoanPersistenceAdapterContract {

    static final UserId DEMO_USER = UserId.of("00000000-0000-0000-0000-000000000001");
    static final UserId DEMO_ADMIN = UserId.of("00000000-0000-0000-0000-000000000002");

    @Autowired
    LoanRepository loans;

    @Autowired
    UserRepository users;

    @Autowired
    EntityManager em;

    @Test
    void schemaValidatesAndSeedIsPresent() {
        assertThat(users.findByEmail(new Email("usuario@test.com"))).isPresent();
        assertThat(users.findByEmail(new Email("admin@test.com"))).isPresent();
        assertThat(loans.findByApplicant(DEMO_USER, null, new PageQuery(0, 10, null, false)).totalElements())
                .isEqualTo(4);
        Loan rejected = loans.findById(LoanId.of("00000000-0000-0000-0000-000000000104")).orElseThrow();
        assertThat(rejected.status()).isEqualTo(LoanStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Ingresos insuficientes para el monto solicitado.");
        assertThat(rejected.decidedBy()).isEqualTo(DEMO_ADMIN);
        assertThat(rejected.version()).isZero();
    }

    @Test
    void savingNewLoanInserts() {
        Loan fresh = newLoan();

        Loan saved = loans.save(fresh);
        em.flush();
        em.clear();

        assertThat(saved.version()).isZero();
        Loan read = loans.findById(fresh.id()).orElseThrow();
        assertThat(read.amount().value()).isEqualByComparingTo("1500000.00");
        assertThat(read.term().months()).isEqualTo(12);
        assertThat(read.requestedAt()).isEqualTo(fresh.requestedAt());
    }

    @Test
    void staleVersionFailsWithOptimisticLock() {
        Loan saved = loans.save(newLoan());
        em.flush();
        em.clear();

        Loan first = saved.cancel();
        Loan second = saved.update(new Amount(new BigDecimal("2000.00")), new LoanTerm(6), new LoanPolicy(
                new BigDecimal("100.00"), new BigDecimal("50000000.00"), 1, 120, 3));

        Loan afterFirst = loans.save(first);
        em.flush();
        em.clear();
        assertThat(afterFirst.version()).isEqualTo(1L);

        assertThatThrownBy(() -> {
                    loans.save(second);
                    em.flush();
                })
                .isExactlyInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void findByApplicantFiltersAndPages() {
        PageResult<Loan> pending = loans.findByApplicant(DEMO_USER, LoanStatus.PENDING, new PageQuery(0, 1, "amount", true));

        assertThat(pending.totalElements()).isEqualTo(2);
        assertThat(pending.items()).hasSize(1);
        assertThat(pending.items().get(0).amount().value()).isEqualByComparingTo("1000.00");

        PageResult<Loan> second = loans.findByApplicant(DEMO_USER, LoanStatus.PENDING, new PageQuery(1, 1, "amount", true));
        assertThat(second.items().get(0).amount().value()).isEqualByComparingTo("30000.00");

        PageResult<Loan> defaultOrder = loans.findByApplicant(DEMO_USER, null, new PageQuery(0, 10, null, false));
        assertThat(defaultOrder.items()).extracting(Loan::requestedAt).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());

        assertThatThrownBy(() -> loans.findByApplicant(DEMO_USER, null, new PageQuery(0, 10, "version", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminFindAllJoinsApplicantEmailAndFiltersByEmail() {
        var all = loans.findAll(null, null, new PageQuery(0, 10, null, false));
        assertThat(all.totalElements()).isEqualTo(4);
        assertThat(all.items()).allSatisfy(v -> {
            assertThat(v.applicantEmail()).isEqualTo("usuario@test.com");
            assertThat(v.applicantName()).isEqualTo("Usuario Demo");
        });

        var byEmail = loans.findAll(LoanStatus.APPROVED, new Email("usuario@test.com"), new PageQuery(0, 10, "amount", true));
        assertThat(byEmail.totalElements()).isEqualTo(1);
        assertThat(byEmail.items().get(0).loan().status()).isEqualTo(LoanStatus.APPROVED);

        assertThat(loans.findAll(null, new Email("admin@test.com"), new PageQuery(0, 10, null, false)).totalElements())
                .isZero();
    }

    @Test
    void countByApplicantAndStatus() {
        assertThat(loans.countByApplicantAndStatus(DEMO_USER, LoanStatus.PENDING)).isEqualTo(2);
        assertThat(loans.countByApplicantAndStatus(DEMO_USER, LoanStatus.CANCELLED)).isZero();
        assertThat(loans.countByApplicantAndStatus(DEMO_ADMIN, LoanStatus.PENDING)).isZero();
    }

    private static Loan newLoan() {
        return new Loan(
                LoanId.newId(), DEMO_USER, new Amount(new BigDecimal("1500000.00")), new LoanTerm(12),
                LoanStatus.PENDING, Instant.parse("2026-11-20T12:00:00Z"), null, null, null, null);
    }
}
