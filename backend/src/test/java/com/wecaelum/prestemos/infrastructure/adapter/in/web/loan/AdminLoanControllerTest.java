package com.wecaelum.prestemos.infrastructure.adapter.in.web.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CancelLoanUseCase;
import com.wecaelum.prestemos.application.port.in.DecideLoanUseCase;
import com.wecaelum.prestemos.application.port.in.QueryLoansUseCase;
import com.wecaelum.prestemos.application.port.in.RequestLoanUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateLoanUseCase;
import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.ProblemDetailFactory;
import com.wecaelum.prestemos.infrastructure.config.ClockConfig;
import com.wecaelum.prestemos.infrastructure.security.UserAuthSnapshotProvider;
import com.wecaelum.prestemos.infrastructure.security.WebSliceSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {LoanController.class, AdminLoanController.class})
@Import({WebSliceSecurityConfig.class, GlobalExceptionHandler.class, ProblemDetailFactory.class, ClockConfig.class})
class AdminLoanControllerTest {

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

    @Test
    void rejectRequiresReason() throws Exception {
        String url = "/api/v1/loans/" + LoanId.newId().value() + "/reject";
        for (String body : List.of("{\"reason\":\"   \"}", "{}", "{\"reason\":\"abc\"}")) {
            mvc.perform(post(url).with(LoanControllerTest.as(LoanControllerTest.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(decideLoan);
    }

    @Test
    void rejectDelegatesWithReason() throws Exception {
        Loan rejected = LoanControllerTest.loan("1000", LoanStatus.PENDING);
        when(decideLoan.reject(eq(rejected.id()), eq(LoanControllerTest.ADMIN.id()), eq("Ingresos insuficientes")))
                .thenReturn(rejected);

        mvc.perform(post("/api/v1/loans/" + rejected.id().value() + "/reject")
                        .with(LoanControllerTest.as(LoanControllerTest.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ingresos insuficientes\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void approveOfDecidedLoanIs409() throws Exception {
        LoanId id = LoanId.newId();
        when(decideLoan.approve(id, LoanControllerTest.ADMIN.id()))
                .thenThrow(new LoanNotPendingException(LoanStatus.APPROVED));

        mvc.perform(post("/api/v1/loans/" + id.value() + "/approve").with(LoanControllerTest.as(LoanControllerTest.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_PENDING"));
    }

    @Test
    void listIncludesApplicantEmail() throws Exception {
        Loan one = LoanControllerTest.loan("1000", LoanStatus.PENDING);
        when(queryLoans.listAll(eq(LoanStatus.PENDING), eq(new Email("usuario@test.com")), any()))
                .thenReturn(new PageResult<>(List.of(new LoanView(one, "usuario@test.com", "Usuario Demo")), 1, 5, 6));

        mvc.perform(get("/api/v1/loans?status=PENDING&applicantEmail=Usuario@Test.com&page=1&size=5&sort=amount,asc")
                        .with(LoanControllerTest.as(LoanControllerTest.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicantEmail").value("usuario@test.com"))
                .andExpect(jsonPath("$.content[0].applicantName").value("Usuario Demo"))
                .andExpect(jsonPath("$.content[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.totalPages").value(2));
        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(queryLoans).listAll(any(), any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PageQuery(1, 5, "amount", true));
    }

    @Test
    void malformedApplicantEmailIs400() throws Exception {
        mvc.perform(get("/api/v1/loans?applicantEmail=nope").with(LoanControllerTest.as(LoanControllerTest.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidStatusFilterIs400() throws Exception {
        mvc.perform(get("/api/v1/loans?status=NOPE").with(LoanControllerTest.as(LoanControllerTest.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void userRoleCannotListAllNorDecide() throws Exception {
        mvc.perform(get("/api/v1/loans").with(LoanControllerTest.as(LoanControllerTest.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/" + LoanId.newId().value() + "/approve")
                        .with(LoanControllerTest.as(LoanControllerTest.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queryLoans, decideLoan);
    }
}
