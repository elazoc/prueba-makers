package com.wecaelum.prestemos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** User administration over real HTTP, on its own in-memory database so committed rows stay out of other tests. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:useradmin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class UserAdminIT {

    private static final String PASSWORD = "12345678";

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    record Reply(int status, JsonNode body, HttpResponse<String> raw) {}

    private Reply call(String method, String path, String token, String body, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), BodyHandlers.ofString());
        JsonNode node = response.body().isBlank() ? null : json.readTree(response.body());
        return new Reply(response.statusCode(), node, response);
    }

    private Reply loginReply(String email, String password) throws Exception {
        return call("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String token(Reply login) {
        assertThat(login.status()).isEqualTo(200);
        return login.body().get("accessToken").asString();
    }

    @Test
    void deactivationTakesEffectImmediately() throws Exception {
        String email = "baja-" + UUID.randomUUID() + "@test.com";
        assertThat(call("POST", "/api/v1/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Persona Baja\"}").status())
                .isEqualTo(201);
        Reply login = loginReply(email, PASSWORD);
        String userToken = token(login);
        String cookie = login.raw().headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("prestemos_refresh=")).findFirst().orElseThrow().split(";")[0];
        String admin = token(loginReply("admin@test.com", "123"));

        String loanId = call("POST", "/api/v1/loans", userToken, "{\"amount\":1000,\"termMonths\":12}")
                .body().get("id").asString();
        String userId = call("GET", "/api/v1/users/me", userToken, null).body().get("id").asString();

        assertThat(call("DELETE", "/api/v1/users/" + userId, admin, null).status()).isEqualTo(204);

        Reply after = call("GET", "/api/v1/users/me", userToken, null);
        assertThat(after.status()).isEqualTo(401);
        assertThat(after.body().get("code").asString()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(call("POST", "/api/v1/auth/refresh", null, null,
                "Cookie", cookie, "X-Requested-With", "prestemos").status()).isEqualTo(401);
        assertThat(loginReply(email, PASSWORD).status()).isEqualTo(401);

        Reply loan = call("GET", "/api/v1/loans/" + loanId, admin, null);
        assertThat(loan.status()).isEqualTo(200);
        assertThat(loan.body().get("status").asString()).isEqualTo("PENDING");
        Reply stored = call("GET", "/api/v1/users/" + userId, admin, null);
        assertThat(stored.body().get("active").asBoolean()).isFalse();
        assertThat(stored.raw().body()).doesNotContainIgnoringCase("password");
    }

    @Test
    void adminCannotChangeOwnRoleNorDeactivateSelf() throws Exception {
        String admin = token(loginReply("admin@test.com", "123"));
        String adminId = call("GET", "/api/v1/users/me", admin, null).body().get("id").asString();

        Reply demote = call("PUT", "/api/v1/users/" + adminId, admin,
                "{\"fullName\":\"Administrador\",\"role\":\"USER\",\"active\":true}");
        assertThat(demote.status()).isEqualTo(409);
        assertThat(demote.body().get("code").asString()).isEqualTo("CANNOT_DEMOTE_SELF");

        Reply self = call("DELETE", "/api/v1/users/" + adminId, admin, null);
        assertThat(self.status()).isEqualTo(409);
        assertThat(self.body().get("code").asString()).isEqualTo("CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void adminCreatesUpdatesAndListsWhileUserIsForbidden() throws Exception {
        String admin = token(loginReply("admin@test.com", "123"));
        String email = "alta-" + UUID.randomUUID() + "@test.com";

        Reply created = call("POST", "/api/v1/users", admin,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Persona Alta\",\"role\":\"USER\"}");
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().get("id").asString();
        assertThat(created.raw().headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/api/v1/users/" + id));

        Reply updated = call("PUT", "/api/v1/users/" + id, admin,
                "{\"fullName\":\"Persona Alta\",\"role\":\"ADMIN\",\"active\":true}");
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("role").asString()).isEqualTo("ADMIN");

        Reply list = call("GET", "/api/v1/users?role=ADMIN&active=true&sort=email,asc&size=100", admin, null);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.raw().body()).contains(email);

        String user = token(loginReply(email, PASSWORD));
        // The new person is an admin now, so a plain user token is needed for the 403 check.
        assertThat(call("GET", "/api/v1/users", user, null).status()).isEqualTo(200);
        String plain = token(loginReply("usuario@test.com", "123"));
        assertThat(call("GET", "/api/v1/users", plain, null).status()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/users/me", plain, null).status()).isEqualTo(200);
    }
}
