package com.wecaelum.prestemos.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Stateless JWT security for the API, plus an open chain for the H2 console when it is enabled (design section 8).
 * The refresh token travels in an {@code HttpOnly} cookie limited to {@code /api/v1/auth}; the endpoints that use
 * it are guarded by {@link RequestedWithHeaderFilter} instead of CSRF tokens.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshProperties.class)
class SecurityConfig {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    /** H2 console: development only. It serves HTML in frames and posts forms, so it gets its own relaxed chain. */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            UserAuthSnapshotProvider snapshots,
            ProblemAuthenticationEntryPoint entryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            ProblemResponseWriter problemWriter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The access token travels in the Authorization header, which browsers never send on their own. The
                // only cookie is the refresh token's, and its endpoints require X-Requested-With (see the filter).
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(new RequestedWithHeaderFilter(problemWriter), CorsFilter.class)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> headers.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        PathPatternRequestMatcher.pathPattern("/api/**"),
                        new ContentSecurityPolicyHeaderWriter(API_CSP))))
                .authorizeHttpRequests(authorize -> authorize
                        // SSE async dispatches and error dispatches were already authorized on the original request.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/loans/*/approve", "/api/v1/loans/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .requestMatchers("/api/v1/users/**", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/loans").hasRole("USER")
                        .requestMatchers("/api/v1/me/**").hasRole("USER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new UserJwtAuthenticationConverter(snapshots)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Ignores the Authorization header on the auth endpoints: they take no access token, and a stale one the
     * client still sends must not turn a login or a refresh into a 401.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        RequestMatcher authEndpoints = PathPatternRequestMatcher.pathPattern("/api/v1/auth/**");
        return request -> authEndpoints.matches(request) ? null : resolver.resolve(request);
    }

    /** Credentials are allowed so the browser sends the refresh cookie; hence an explicit origin list, never "*". */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(
                List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, RequestedWithHeaderFilter.HEADER));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
