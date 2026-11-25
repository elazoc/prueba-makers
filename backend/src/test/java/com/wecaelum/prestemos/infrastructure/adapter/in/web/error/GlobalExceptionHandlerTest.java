package com.wecaelum.prestemos.infrastructure.adapter.in.web.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidCredentialsException;
import com.wecaelum.prestemos.application.exception.InvalidRefreshTokenException;
import com.wecaelum.prestemos.domain.exception.CannotDeactivateSelfException;
import com.wecaelum.prestemos.domain.exception.CannotDemoteSelfException;
import com.wecaelum.prestemos.domain.exception.DomainException;
import com.wecaelum.prestemos.domain.exception.EmailAlreadyRegisteredException;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.exception.UserNotFoundException;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.infrastructure.config.ClockConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerTest.ThrowingController.class, ProblemDetailFactory.class, ClockConfig.class})
class GlobalExceptionHandlerTest {

    static class UnmappedDomainException extends DomainException {
        UnmappedDomainException() {
            super("mensaje de negocio sin mapear");
        }

        @Override
        public String code() {
            return "UNMAPPED";
        }
    }

    record Body(@NotNull Integer amount, @NotNull String name) {}

    @RestController
    static class ThrowingController {

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Body body) {}

        @GetMapping("/test/loans/{id}")
        void loan(@PathVariable UUID id) {}

        @GetMapping("/test/size")
        void size(@RequestParam @Min(1) int size) {}

        @GetMapping("/test/throw/{kind}")
        void fail(@PathVariable String kind) {
            switch (kind) {
                case "loan-not-found" -> throw new LoanNotFoundException();
                case "user-not-found" -> throw new UserNotFoundException();
                case "loan-not-pending" -> throw new LoanNotPendingException(LoanStatus.APPROVED);
                case "policy" -> throw new LoanPolicyViolationException("Monto fuera de política.");
                case "email" -> throw new EmailAlreadyRegisteredException();
                case "deactivate-self" -> throw new CannotDeactivateSelfException();
                case "demote-self" -> throw new CannotDemoteSelfException();
                case "credentials" -> throw new InvalidCredentialsException();
                case "disabled" -> throw new DisabledAccountException();
                case "refresh" -> throw new InvalidRefreshTokenException();
                case "optimistic" -> throw new ObjectOptimisticLockingFailureException("Loan", "x");
                case "illegal-argument" -> throw new IllegalArgumentException("El monto debe ser positivo.");
                case "unmapped" -> throw new UnmappedDomainException();
                default -> throw new IllegalStateException("secreto interno jdbc:postgresql://db");
            }
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void validationErrorListsFieldsInSpanish() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Revisa los datos enviados: hay campos con errores."))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("no debe ser nulo"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedUuidPathIs400() throws Exception {
        mvc.perform(get("/test/loans/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("El valor 'abc' no es válido para 'id'."));
    }

    @Test
    void methodParameterConstraintIs400WithErrors() throws Exception {
        mvc.perform(get("/test/size").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("No pudimos leer la solicitud. Revisa el formato JSON."));
    }

    static Stream<Arguments> mappings() {
        return Stream.of(
                Arguments.of("loan-not-found", 404, "LOAN_NOT_FOUND"),
                Arguments.of("user-not-found", 404, "USER_NOT_FOUND"),
                Arguments.of("loan-not-pending", 409, "LOAN_NOT_PENDING"),
                Arguments.of("email", 409, "EMAIL_ALREADY_REGISTERED"),
                Arguments.of("deactivate-self", 409, "CANNOT_DEACTIVATE_SELF"),
                Arguments.of("demote-self", 409, "CANNOT_DEMOTE_SELF"),
                Arguments.of("policy", 422, "LOAN_POLICY_VIOLATION"),
                Arguments.of("credentials", 401, "INVALID_CREDENTIALS"),
                Arguments.of("disabled", 401, "ACCOUNT_DISABLED"),
                Arguments.of("refresh", 401, "REFRESH_TOKEN_INVALID"),
                Arguments.of("optimistic", 409, "CONCURRENT_MODIFICATION"),
                Arguments.of("illegal-argument", 400, "VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void domainExceptionsMapToStatusAndCode(String kind, int status, String code) throws Exception {
        mvc.perform(get("/test/throw/" + kind))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(status));
    }

    @Test
    void domainExceptionMessageIsTheDetail() throws Exception {
        mvc.perform(get("/test/throw/policy"))
                .andExpect(jsonPath("$.detail").value("Monto fuera de política."));
        mvc.perform(get("/test/throw/optimistic"))
                .andExpect(jsonPath("$.detail").value("Otra persona cambió este registro. Recarga e inténtalo de nuevo."));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/test/nada"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Esa ruta no existe."));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        mvc.perform(get("/test/throw/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Algo falló de nuestro lado. Intenta de nuevo en un momento."))
                .andExpect(jsonPath("$.detail", not(containsString("jdbc"))))
                .andExpect(jsonPath("$.errorId").exists())
                .andExpect(content().string(not(containsString("secreto"))));
    }

    @Test
    void typeIsKebabCase() throws Exception {
        mvc.perform(get("/test/throw/loan-not-pending"))
                .andExpect(jsonPath("$.type").value("/problems/loan-not-pending"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.timestamp", startsWith("20")));
    }

    @Test
    void unmappedDomainExceptionIsAnInternalError() throws Exception {
        mvc.perform(get("/test/throw/unmapped"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errorId").exists())
                .andExpect(content().string(not(containsString("sin mapear"))));
    }

    @Test
    void methodNotAllowedHasCodeAndTimestamp() throws Exception {
        mvc.perform(post("/test/size"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.type").value("/problems/method-not-allowed"))
                .andExpect(jsonPath("$.detail").value("Este método no está permitido en esta ruta."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedMediaTypeHasCodeAndTimestamp() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.detail").value("El tipo de contenido no es compatible. Envía JSON."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingRequestParameterIs400() throws Exception {
        mvc.perform(get("/test/size"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Falta el parámetro 'size'."))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
