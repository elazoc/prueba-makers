package com.wecaelum.prestemos.infrastructure.adapter.in.web.error;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds RFC 9457 problem details with the fields every error response of the API shares. */
@Component
public class ProblemDetailFactory {

    /** One invalid field of a request. */
    public record FieldViolation(String field, String message) {}

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now(clock));
        return problem;
    }

    public ProblemDetail validation(String detail, List<FieldViolation> errors) {
        ProblemDetail problem = create(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
        problem.setProperty("errors", errors);
        return problem;
    }
}
