package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CancelLoanUseCase;
import com.wecaelum.prestemos.application.port.in.DecideLoanUseCase;
import com.wecaelum.prestemos.application.port.in.QueryLoansUseCase;
import com.wecaelum.prestemos.application.port.in.RequestLoanCommand;
import com.wecaelum.prestemos.application.port.in.RequestLoanUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateLoanCommand;
import com.wecaelum.prestemos.application.port.in.UpdateLoanUseCase;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.model.Amount;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.LoanTerm;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.config.ClockConfig;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import com.wecaelum.prestemos.infrastructure.security.UserAuthSnapshotProvider;
import com.wecaelum.prestemos.infrastructure.security.WebSliceSecurityConfig;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.ProblemDetailFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Real security chain imported through {@link WebSliceSecurityConfig}; callers are injected with
 * {@code authentication(...)} carrying an {@link AuthenticatedUser}, so role rules are the production ones.
 */
@WebMvcTest(controllers = {LoanController.class, AdminLoanController.class})
@Import({WebSliceSecurityConfig.class, GlobalExceptionHandler.class, ProblemDetailFactory.class, ClockConfig.class})
class LoanControllerTest {

    static final AuthenticatedUser USER =
            new AuthenticatedUser(UserId.of("00000000-0000-0000-0000-000000000001"), new Email("usuario@test.com"), Role.USER);
    static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(UserId.of("00000000-0000-0000-0000-000000000002"), new Email("admin@test.com"), Role.ADMIN);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RequestLoanUseCase requestLoan;
    @MockitoBean
    UpdateLoanUseCase updateLoan;
    @MockitoBean
    CancelLoanUseCase cancelLoan;
    @MockitoBean
    QueryLoansUseCase queryLoans;
    @MockitoBean
    DecideLoanUseCase decideLoan;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    UserAuthSnapshotProvider snapshots;

    static RequestPostProcessor as(AuthenticatedUser who) {
        return authentication(new UsernamePasswordAuthenticationToken(
                who, null, AuthorityUtils.createAuthorityList("ROLE_" + who.role().name())));
    }

    static Loan loan(String amount, LoanStatus status) {
        return new Loan(LoanId.newId(), USER.id(), new Amount(new BigDecimal(amount)), new LoanTerm(12), status,
                Instant.parse("2026-11-25T10:00:00Z"), null, null, null, 0L);
    }

    @Test
    void createReturns201WithLocationAndStringAmount() throws Exception {
        Loan created = loan("1500000", LoanStatus.PENDING);
        when(requestLoan.request(any())).thenReturn(created);

        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1500000, \"termMonths\": 12}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/loans/" + created.id().value())))
                .andExpect(jsonPath("$.amount").value("1500000.00"))
                .andExpect(jsonPath("$.id").value(created.id().value().toString()))
                .andExpect(jsonPath("$.applicantId").value(USER.id().value().toString()))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAt").value("2026-11-25T10:00:00Z"))
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        ArgumentCaptor<RequestLoanCommand> captor = ArgumentCaptor.forClass(RequestLoanCommand.class);
        verify(requestLoan).request(captor.capture());
        assertThat(captor.getValue().applicantId()).isEqualTo(USER.id());
        assertThat(captor.getValue().termMonths()).isEqualTo(12);
    }

    @Test
    void amountAcceptsAJsonString() throws Exception {
        when(requestLoan.request(any())).thenReturn(loan("250.50", LoanStatus.PENDING));

        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"250.50\", \"termMonths\": 6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("250.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.999", "-5", "0", "\"abc\"", "null", "99999999999999999", "1e400"})
    void amountShapeErrorsAre400(String amount) throws Exception {
        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + ", \"termMonths\": 12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(requestLoan);
    }

    @Test
    void missingAmountAndBadTermAre400() throws Exception {
        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000, \"termMonths\": 0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requestLoan);
    }

    @Test
    void scientificNotationReachesPolicy() throws Exception {
        when(requestLoan.request(any())).thenThrow(new LoanPolicyViolationException("El monto máximo es 50000000.00."));

        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1e10, \"termMonths\": 12}"))
                .andExpect(status().isUnprocessableContent());
        ArgumentCaptor<RequestLoanCommand> captor = ArgumentCaptor.forClass(RequestLoanCommand.class);
        verify(requestLoan).request(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("10000000000");
    }

    @Test
    void policyViolationIs422() throws Exception {
        when(requestLoan.request(any())).thenThrow(new LoanPolicyViolationException("Ya tienes 3 solicitudes pendientes."));

        mvc.perform(post("/api/v1/loans").with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000, \"termMonths\": 12}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LOAN_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.detail").value("Ya tienes 3 solicitudes pendientes."));
    }

    static Stream<String> abusiveQueries() {
        return Stream.of("size=10000", "size=0", "page=-1", "sort=passwordHash", "sort=amount,sideways", "status=NOPE", "page=abc");
    }

    @ParameterizedTest
    @MethodSource("abusiveQueries")
    void paginationAbuseIs400(String query) throws Exception {
        mvc.perform(get("/api/v1/me/loans?" + query).with(as(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(queryLoans);
    }

    @Test
    void mineReturnsFlatPageWithDefaults() throws Exception {
        Loan one = loan("1000", LoanStatus.PENDING);
        when(queryLoans.listMine(eq(USER.id()), eq(LoanStatus.PENDING), any()))
                .thenReturn(new PageResult<>(List.of(one), 0, 20, 41));

        mvc.perform(get("/api/v1/me/loans?status=PENDING").with(as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));
        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(queryLoans).listMine(eq(USER.id()), eq(LoanStatus.PENDING), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PageQuery(0, 20, "requestedAt", false));
    }

    @Test
    void foreignLoanIs404() throws Exception {
        LoanId id = LoanId.newId();
        when(queryLoans.get(id, USER.id(), false)).thenThrow(new LoanNotFoundException());

        mvc.perform(get("/api/v1/loans/" + id.value()).with(as(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(get("/api/v1/loans/not-a-uuid").with(as(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminReadsAnyLoanWithAdminFlag() throws Exception {
        Loan one = loan("1000", LoanStatus.PENDING);
        when(queryLoans.get(one.id(), ADMIN.id(), true)).thenReturn(one);

        mvc.perform(get("/api/v1/loans/" + one.id().value()).with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(one.id().value().toString()));
    }

    @Test
    void updateDelegatesWithCaller() throws Exception {
        Loan updated = loan("2000", LoanStatus.PENDING);
        when(updateLoan.update(any())).thenReturn(updated);

        mvc.perform(put("/api/v1/loans/" + updated.id().value()).with(as(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 2000, \"termMonths\": 24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("2000.00"));
        ArgumentCaptor<UpdateLoanCommand> captor = ArgumentCaptor.forClass(UpdateLoanCommand.class);
        verify(updateLoan).update(captor.capture());
        assertThat(captor.getValue().requesterId()).isEqualTo(USER.id());
        assertThat(captor.getValue().loanId()).isEqualTo(updated.id());
        assertThat(captor.getValue().termMonths()).isEqualTo(24);
    }

    @Test
    void cancelReturns204() throws Exception {
        LoanId id = LoanId.newId();

        mvc.perform(delete("/api/v1/loans/" + id.value()).with(as(USER)))
                .andExpect(status().isNoContent());
        verify(cancelLoan).cancel(id, USER.id());
    }

    @Test
    void anonymousIs401AndAdminCannotCreate() throws Exception {
        mvc.perform(get("/api/v1/me/loans")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/loans").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000, \"termMonths\": 12}"))
                .andExpect(status().isForbidden());
    }
}
