package com.wecaelum.prestemos.infrastructure.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Brings the real security chain (role rules, problem+json handlers) into {@code @WebMvcTest} slices. Lives in this
 * package because the chain's collaborators are package-private. Tests also need a {@code @MockitoBean JwtDecoder}
 * and {@code UserAuthSnapshotProvider}, and authenticate with {@code .with(authentication(...))}.
 */
@TestConfiguration
@Import({
    SecurityConfig.class,
    ProblemAuthenticationEntryPoint.class,
    ProblemAccessDeniedHandler.class,
    ProblemResponseWriter.class
})
public class WebSliceSecurityConfig {}
