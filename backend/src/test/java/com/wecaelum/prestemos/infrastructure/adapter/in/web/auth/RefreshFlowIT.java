package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateUserCommand;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The refresh token flow over HTTP, on H2 with the seeded users. Each test runs in a rolled-back transaction
 * (MockMvc calls share the test thread), except {@link OutsideTheGrace}, which commits so it also proves that
 * a rejected refresh keeps the revocations it made.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshFlowIT {

    private static final String COOKIE = "prestemos_refresh";
    private static final String REQUESTED_WITH = "X-Requested-With";
    private static final UserId SEEDED_USER = UserId.of("00000000-0000-0000-0000-000000000001");
    private static final UserId SEEDED_ADMIN = UserId.of("00000000-0000-0000-0000-000000000002");
    private static final Pattern COOKIE_VALUE = Pattern.compile(COOKIE + "=([^;]*)");
    private static final Pattern MAX_AGE = Pattern.compile("Max-Age=(\\d+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageUsersUseCase users;

    @Test
    void loginSetsAHardenedCookieAndKeepsTheTokenOutOfTheBody() throws Exception {
        MvcResult result = login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = setCookie(result);
        assertThat(setCookie)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        assertThat(maxAge(setCookie)).isBetween(7L * 24 * 3600 - 5, 7L * 24 * 3600);
        String token = cookieValue(setCookie);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
    }

    @Test
    void refreshRotatesTheCookieAndReturnsANewSession() throws Exception {
        String first = cookieValue(setCookie(login().andExpect(status().isOk()).andReturn()));

        MvcResult refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("usuario@test.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = setCookie(refreshed);
        String second = cookieValue(setCookie);
        assertThat(second).isNotEqualTo(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        assertThat(refreshed.getResponse().getContentAsString()).doesNotContain(second);

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void theOldTokenInsideTheGraceIs401ButTheNewOneStillWorks() throws Exception {
        String first = loginToken();
        String second = cookieValue(setCookie(refresh(first).andExpect(status().isOk()).andReturn()));

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.detail").value("Tu sesión venció. Inicia sesión de nuevo."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutOrWithAnUnknownCookieIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header(REQUESTED_WITH, "prestemos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        refresh("no-existe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refreshAndLogoutRequireTheRequestedWithHeader() throws Exception {
        String token = loginToken();

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value(containsString("X-Requested-With")))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, token)).header(REQUESTED_WITH, "otro"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The rejected requests neither rotated nor revoked the token.
        refresh(token).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        String token = loginToken();

        MvcResult result = logout(token).andExpect(status().isNoContent()).andReturn();

        String setCookie = setCookie(result);
        assertThat(cookieValue(setCookie)).isEmpty();
        assertThat(maxAge(setCookie)).isZero();
        assertThat(setCookie).contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure");
        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logoutWithoutCookieIs204() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout").header(REQUESTED_WITH, "prestemos"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(maxAge(setCookie(result))).isZero();

        logout("no-existe").andExpect(status().isNoContent());
    }

    @Test
    void deactivatedUserCannotRefreshAndLosesTheSession() throws Exception {
        String token = loginToken();
        users.deactivate(SEEDED_USER, SEEDED_ADMIN);

        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        users.update(new UpdateUserCommand(SEEDED_USER, SEEDED_ADMIN, "Usuario Demo", Role.USER, true));
        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refreshCarriesTheCurrentRole() throws Exception {
        String token = loginToken();
        users.update(new UpdateUserCommand(SEEDED_USER, SEEDED_ADMIN, "Usuario Demo", Role.ADMIN, true));

        refresh(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void aStaleBearerTokenDoesNotBlockTheAuthEndpoints() throws Exception {
        String token = loginToken();

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, token))
                        .header(REQUESTED_WITH, "prestemos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer vencido.o.roto"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer vencido.o.roto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario@test.com\",\"password\":\"123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void corsAllowsCredentialsAndTheRequestedWithHeaderForTheFront() throws Exception {
        mvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString(REQUESTED_WITH)));

        mvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://otro-sitio.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario@test.com\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    /** With no grace, reusing a rotated token is treated as theft. Runs without a test transaction. */
    @Nested
    @TestPropertySource(properties = "security.refresh.reuse-grace=0s")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class OutsideTheGrace {

        @Test
        void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
            String first = loginToken();
            String second = cookieValue(setCookie(refresh(first).andExpect(status().isOk()).andReturn()));

            refresh(first)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

            refresh(second)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }
    }

    private ResultActions login() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"usuario@test.com\",\"password\":\"123\"}"));
    }

    private String loginToken() throws Exception {
        return cookieValue(setCookie(login().andExpect(status().isOk()).andReturn()));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(COOKIE, token))
                .header(REQUESTED_WITH, "prestemos"));
    }

    private ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie(COOKIE, token))
                .header(REQUESTED_WITH, "prestemos"));
    }

    private static String setCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).as("Set-Cookie").isNotNull().startsWith(COOKIE + "=");
        return header;
    }

    private static String cookieValue(String setCookie) {
        Matcher matcher = COOKIE_VALUE.matcher(setCookie);
        assertThat(matcher.find()).as("cookie value in " + setCookie).isTrue();
        return matcher.group(1);
    }

    private static long maxAge(String setCookie) {
        Matcher matcher = MAX_AGE.matcher(setCookie);
        assertThat(matcher.find()).as("Max-Age in " + setCookie).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
