package com.wecaelum.prestemos.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Stateless JWT security for the API, plus an open chain for the H2 console when it is enabled (design section 8). */
@Configuration(proxyBeanMethods = false)
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
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No cookies: the token travels in the Authorization header, which browsers never send on their own.
                .csrf(AbstractHttpConfigurer::disable)
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
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new UserJwtAuthenticationConverter(snapshots)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
