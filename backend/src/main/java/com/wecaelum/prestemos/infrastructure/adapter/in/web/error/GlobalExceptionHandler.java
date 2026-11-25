package com.wecaelum.prestemos.infrastructure.adapter.in.web.error;

import com.wecaelum.prestemos.application.exception.DisabledAccountException;
import com.wecaelum.prestemos.application.exception.InvalidCredentialsException;
import com.wecaelum.prestemos.domain.exception.DomainException;
import com.wecaelum.prestemos.domain.exception.EmailAlreadyRegisteredException;
import com.wecaelum.prestemos.domain.exception.CannotDeactivateSelfException;
import com.wecaelum.prestemos.domain.exception.CannotDemoteSelfException;
import com.wecaelum.prestemos.domain.exception.LoanNotFoundException;
import com.wecaelum.prestemos.domain.exception.LoanNotPendingException;
import com.wecaelum.prestemos.domain.exception.LoanPolicyViolationException;
import com.wecaelum.prestemos.domain.exception.UserNotFoundException;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.ProblemDetailFactory.FieldViolation;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into an RFC 9457 problem detail (see design section 12). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String VALIDATION_DETAIL = "Revisa los datos enviados: hay campos con errores.";

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler({
        DomainException.class,
        InvalidCredentialsException.class,
        DisabledAccountException.class
    })
    ResponseEntity<ProblemDetail> handleBusiness(RuntimeException ex) {
        String code = ex instanceof DomainException domain
                ? domain.code()
                : ex instanceof InvalidCredentialsException invalid ? invalid.code() : ((DisabledAccountException) ex).code();
        return respond(problems.create(statusOf(ex), code, ex.getMessage()));
    }

    private static HttpStatus statusOf(RuntimeException ex) {
        return switch (ex) {
            case LoanNotFoundException e -> HttpStatus.NOT_FOUND;
            case UserNotFoundException e -> HttpStatus.NOT_FOUND;
            case LoanNotPendingException e -> HttpStatus.CONFLICT;
            case EmailAlreadyRegisteredException e -> HttpStatus.CONFLICT;
            case CannotDeactivateSelfException e -> HttpStatus.CONFLICT;
            case CannotDemoteSelfException e -> HttpStatus.CONFLICT;
            case LoanPolicyViolationException e -> HttpStatus.UNPROCESSABLE_CONTENT;
            case InvalidCredentialsException e -> HttpStatus.UNAUTHORIZED;
            case DisabledAccountException e -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentModification() {
        return respond(problems.create(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Otra persona cambió este préstamo. Recarga e inténtalo de nuevo."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(problems.create(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unexpected error (errorId={})", errorId, ex);
        ProblemDetail problem = problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Algo falló de nuestro lado. Intenta de nuevo en un momento.");
        problem.setProperty("errorId", errorId);
        return respond(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, problems.validation(VALIDATION_DETAIL, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                String.valueOf(result.getMethodParameter().getParameterName()), error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, problems.validation(VALIDATION_DETAIL, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.create(HttpStatus.BAD_REQUEST, VALIDATION_ERROR,
                "No pudimos leer la solicitud. Revisa el formato JSON.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.create(HttpStatus.BAD_REQUEST, VALIDATION_ERROR,
                "El valor '" + ex.getValue() + "' no es válido para '" + ex.getPropertyName() + "'.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.create(HttpStatus.NOT_FOUND, "NOT_FOUND", "Esa ruta no existe.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
