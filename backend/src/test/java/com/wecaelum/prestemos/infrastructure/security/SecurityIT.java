package com.wecaelum.prestemos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateUserCommand;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization runs before routing, so the rules for endpoints that later tasks build are testable now:
 * a denied request gets 401/403, an allowed one reaches the dispatcher and gets 404 until its controller exists.
 * <p>
 * MockMvc calls run in the test thread, inside the rolled-back test transaction, so the throwaway users these
 * tests create, demote or deactivate never touch the seeded ones nor outlive the test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class SecurityIT {

    private static final UserId SEEDED_USER = UserId.of("00000000-0000-0000-0000-000000000001");
    private static final UserId SEEDED_ADMIN = UserId.of("00000000-0000-0000-0000-000000000002");
    private static final String APPROVE = "/api/v1/loans/00000000-0000-0000-0000-000000000101/approve";
    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageUsersUseCase users;

    @Autowired
    private TokenIssuer tokens;

    @Autowired
    private JwtEncoder encoder;

    @LocalServerPort
    private int port;

    @Test
    void noTokenIs401ProblemJson() throws Exception {
        mvc.perform(get("/api/v1/me/loans"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Inicia sesión para continuar."))
                .andExpect(jsonPath("$.type").value("/problems/unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/me/loans"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void tamperedOrExpiredTokenIs401ProblemJson() throws Exception {
        String[] parts = tokens.issue(seededUser()).value().split("\\.");
        char original = parts[2].charAt(10);
        parts[2] = parts[2].substring(0, 10) + (original == 'A' ? 'B' : 'A') + parts[2].substring(11);
        String tampered = String.join(".", parts);

        Instant past = Instant.now().minusSeconds(7200);
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("prestemos")
                .subject(SEEDED_USER.value().toString())
                .issuedAt(past)
                .expiresAt(past.plusSeconds(3600))
                .claim("email", "usuario@test.com")
                .claim("name", "Usuario Demo")
                .claim("roles", List.of("USER"))
                .build();
        String expired = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), expiredClaims))
                .getTokenValue();

        for (String token : List.of(tampered, expired, "no-es-un-jwt")) {
            mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void tokenForUnknownUserIs401Unauthenticated() throws Exception {
        User ghost = User.register(new Email("fantasma@test.com"),
                "{bcrypt}x", "Fantasma", Role.ADMIN, Instant.now());

        mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, bearer(ghost)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deactivatedUserTokenIs401AccountDisabled() throws Exception {
        User user = freshUser(Role.USER);
        String token = bearer(user);
        mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notDeniedBySecurity());

        users.deactivate(user.id(), SEEDED_ADMIN);

        mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.detail").value("Tu cuenta está desactivada. Escribe al administrador."));
    }

    @Test
    void roleComesFromDatabaseNotToken() throws Exception {
        User admin = freshUser(Role.ADMIN);
        String token = bearer(admin);
        mvc.perform(post(APPROVE).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notDeniedBySecurity());

        users.update(new UpdateUserCommand(admin.id(), SEEDED_ADMIN, admin.fullName(), Role.USER, true));

        mvc.perform(post(APPROVE).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void userCannotApprove() throws Exception {
        mvc.perform(post(APPROVE).header(HttpHeaders.AUTHORIZATION, bearer(seededUser())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para esta acción."))
                .andExpect(jsonPath("$.instance").value(APPROVE));
    }

    @Test
    void userIsForbiddenOnAdminRoutes() throws Exception {
        String token = bearer(seededUser());

        mvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/00000000-0000-0000-0000-000000000101/reject")
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + SEEDED_USER.value()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }

    @Test
    void adminIsForbiddenOnUserOnlyRoutes() throws Exception {
        String token = bearer(seededAdmin());

        mvc.perform(post("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }

    @Test
    void anyAuthenticatedUserPassesSecurityOnUsersMe() throws Exception {
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(seededUser())))
                .andExpect(notDeniedBySecurity());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://otro-sitio.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cors-" + UUID.randomUUID()
                                + "@test.com\",\"password\":\"12345678\",\"fullName\":\"Cors Prueba\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location"));
    }

    @Test
    void apiResponsesCarryCsp() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario@test.com\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        mvc.perform(get("/api/v1/me/loans"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", API_CSP));

        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void h2ConsoleIsReachable() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isBetween(200, 399);
            assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("SAMEORIGIN");
            assertThat(response.headers().firstValue("Content-Security-Policy")).isEmpty();
        }
    }

    private static ResultMatcher notDeniedBySecurity() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("security must let the request through to the dispatcher")
                .isNotIn(401, 403);
    }

    private User freshUser(Role role) {
        return users.create(new CreateUserCommand(
                "seguridad-" + UUID.randomUUID() + "@test.com", "12345678", "Prueba Seguridad", role));
    }

    private User seededUser() {
        return users.get(SEEDED_USER);
    }

    private User seededAdmin() {
        return users.get(SEEDED_ADMIN);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.issue(user).value();
    }
}
