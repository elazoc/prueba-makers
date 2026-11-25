package com.wecaelum.prestemos.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.domain.model.LoanStatus;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DomainExceptionsTest {

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(new LoanNotFoundException(), "LOAN_NOT_FOUND", "No encontramos ese préstamo."),
                Arguments.of(new UserNotFoundException(), "USER_NOT_FOUND", "No encontramos ese usuario."),
                Arguments.of(
                        new LoanNotPendingException(LoanStatus.APPROVED),
                        "LOAN_NOT_PENDING",
                        "Este préstamo ya está aprobado: solo se pueden cambiar préstamos pendientes."),
                Arguments.of(
                        new EmailAlreadyRegisteredException(),
                        "EMAIL_ALREADY_REGISTERED",
                        "Ya existe una cuenta con ese email."),
                Arguments.of(
                        new CannotDeactivateSelfException(),
                        "CANNOT_DEACTIVATE_SELF",
                        "No puedes desactivar tu propia cuenta."),
                Arguments.of(
                        new LoanPolicyViolationException("El plazo es inválido."),
                        "LOAN_POLICY_VIOLATION",
                        "El plazo es inválido."));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void exposesStableCodeAndSpanishMessage(DomainException exception, String code, String message) {
        assertThat(exception.code()).isEqualTo(code);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
}
