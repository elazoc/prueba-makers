package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CancelLoanUseCase;
import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.DecideLoanUseCase;
import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.RequestLoanCommand;
import com.wecaelum.prestemos.application.port.in.RequestLoanUseCase;
import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.security.UserAuthSnapshot;
import com.wecaelum.prestemos.infrastructure.security.UserAuthSnapshotProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Ehcache caches in front of the loan queries and the per-request user snapshot, and their eviction after
 * commit. The commands here commit for real, so the class runs on its own in-memory database and every test works
 * on its own fresh user.
 */
@SpringBootTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:cachebehaviour;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class CacheBehaviourIT {

    private static final UserId SEEDED_ADMIN = UserId.of("00000000-0000-0000-0000-000000000002");
    private static final PageQuery FIRST_PAGE = new PageQuery(0, 10, null, false);

    @MockitoSpyBean
    SpringDataLoanRepository springRepo;

    @Autowired
    LoanRepository loans;

    @Autowired
    RequestLoanUseCase requestLoan;

    @Autowired
    DecideLoanUseCase decideLoan;

    @Autowired
    CancelLoanUseCase cancelLoan;

    @Autowired
    ManageUsersUseCase users;

    @Autowired
    UserAuthSnapshotProvider snapshots;

    @Autowired
    DomainEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void secondReadOfLoanHitsCache() {
        Loan loan = requestFor(freshUser());
        clearInvocations(springRepo);

        Loan first = loans.findById(loan.id()).orElseThrow();
        Loan second = loans.findById(loan.id()).orElseThrow();

        verify(springRepo, times(1)).findById(loan.id().value());
        assertThat(second).isEqualTo(first);
    }

    @Test
    void missingLoanIsNotCached() {
        LoanId missing = LoanId.newId();

        assertThat(loans.findById(missing)).isEmpty();

        assertThat(cache("loanById").get(missing.value())).isNull();
    }

    @Test
    void approvalEvictsAfterCommit() {
        Loan loan = requestFor(freshUser());
        assertThat(loans.findById(loan.id()).orElseThrow().status()).isEqualTo(LoanStatus.PENDING);
        assertThat(cache("loanById").get(loan.id().value())).isNotNull();

        decideLoan.approve(loan.id(), SEEDED_ADMIN);

        assertThat(cache("loanById").get(loan.id().value())).isNull();
        assertThat(loans.findById(loan.id()).orElseThrow().status()).isEqualTo(LoanStatus.APPROVED);
    }

    @Test
    void rollbackDoesNotEvict() {
        Loan loan = requestFor(freshUser());
        Loan cached = loans.findById(loan.id()).orElseThrow();

        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now();
            Loan approved = loans.save(cached.approve(SEEDED_ADMIN, now));
            events.publish(LoanApproved.of(approved, now));
            status.setRollbackOnly();
        });
        clearInvocations(springRepo);

        Loan again = loans.findById(loan.id()).orElseThrow();

        verify(springRepo, never()).findById(any());
        // Caches declared in ehcache.xml keep entries by reference (no copier), so it is the very same object.
        assertThat(again).isSameAs(cached);
        assertThat(again.status()).isEqualTo(LoanStatus.PENDING);
    }

    @Test
    void applicantListIsEvictedOnAnyLoanChange() {
        UserId applicant = freshUser();
        Loan first = requestFor(applicant);
        assertThat(loans.findByApplicant(applicant, null, FIRST_PAGE).items()).hasSize(1);
        clearInvocations(springRepo);
        assertThat(loans.findByApplicant(applicant, null, FIRST_PAGE).items()).hasSize(1);
        verify(springRepo, never()).findByApplicant(any(), any(), any());

        requestFor(applicant);
        assertThat(loans.findByApplicant(applicant, null, FIRST_PAGE).items()).hasSize(2);

        cancelLoan.cancel(first.id(), applicant);
        assertThat(loans.findByApplicant(applicant, LoanStatus.CANCELLED, FIRST_PAGE).items())
                .extracting(Loan::id)
                .containsExactly(first.id());
        // The status filter is part of the key: the filtered page must not answer the unfiltered one.
        assertThat(loans.findByApplicant(applicant, null, FIRST_PAGE).items())
                .extracting(Loan::status)
                .containsExactlyInAnyOrder(LoanStatus.PENDING, LoanStatus.CANCELLED);

        // A change to someone else's loan clears every list too: Ehcache cannot evict by key prefix.
        clearInvocations(springRepo);
        decideLoan.approve(requestFor(freshUser()).id(), SEEDED_ADMIN);
        loans.findByApplicant(applicant, null, FIRST_PAGE);
        verify(springRepo, times(1)).findByApplicant(any(), any(), any());
    }

    @Test
    void userChangeEvictsAuthSnapshot() {
        UserId user = freshUser();
        assertThat(snapshots.find(user)).hasValueSatisfying(snapshot -> assertThat(snapshot.active()).isTrue());
        assertThat(cache("userAuthById").get(user.value())).isNotNull();

        users.deactivate(user, SEEDED_ADMIN);

        assertThat(cache("userAuthById").get(user.value())).isNull();
        assertThat(snapshots.find(user)).hasValueSatisfying(snapshot -> assertThat(snapshot.active()).isFalse());
    }

    @Test
    void cachedTypesRoundTripThroughSerialization() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Loan rejected = new Loan(LoanId.newId(), UserId.of(UUID.randomUUID().toString()),
                new Amount(new BigDecimal("1500000.00")), new LoanTerm(24), LoanStatus.REJECTED, now,
                now.plusSeconds(60), SEEDED_ADMIN, "Ingresos insuficientes.", 3L);
        PageResult<Loan> page = new PageResult<>(List.of(rejected), 0, 10, 1);
        UserAuthSnapshot snapshot = new UserAuthSnapshot(SEEDED_ADMIN, Role.ADMIN, true);

        assertThat(roundTrip(rejected)).isEqualTo(rejected);
        assertThat(roundTrip(page)).isEqualTo(page);
        assertThat(roundTrip(snapshot)).isEqualTo(snapshot);
    }

    @Test
    void cacheMetricsAreExposed() {
        Loan loan = requestFor(freshUser());
        loans.findById(loan.id());
        loans.findById(loan.id());

        assertThat(meterRegistry.find("cache.gets").tag("cache", "loanById").functionCounters()).isNotEmpty();
        assertThat(meterRegistry.get("cache.gets").tags("cache", "loanById", "result", "hit")
                .functionCounter().count()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("cache.gets").tag("cache", "loansByApplicant").functionCounters())
                .isNotEmpty();
        assertThat(meterRegistry.find("cache.gets").tag("cache", "userAuthById").functionCounters())
                .isNotEmpty();
    }

    private UserId freshUser() {
        return users.create(new CreateUserCommand(
                "cache-" + UUID.randomUUID() + "@test.com", "12345678", "Prueba Cache", Role.USER)).id();
    }

    private Loan requestFor(UserId applicant) {
        return requestLoan.request(new RequestLoanCommand(applicant, new BigDecimal("1000.00"), 12));
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        assertThat(cache).as("cache %s", name).isNotNull();
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
