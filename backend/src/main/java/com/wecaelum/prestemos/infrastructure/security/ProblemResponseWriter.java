package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes Problem Details from the security filters, which run before Spring MVC and its message converters.
 * Same shape as {@code GlobalExceptionHandler}'s responses.
 */
@Component
class ProblemResponseWriter {

    private final ProblemDetailFactory problems;
    private final JsonMapper mapper;

    ProblemResponseWriter(ProblemDetailFactory problems, JsonMapper jsonMapper) {
        this.problems = problems;
        // The mixin flattens the code and timestamp properties to top-level fields, as the MVC converter does.
        this.mapper = jsonMapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        ProblemDetail problem = problems.create(status, code, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
