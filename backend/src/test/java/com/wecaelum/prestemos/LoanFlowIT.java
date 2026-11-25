package com.wecaelum.prestemos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP against the running app and its H2 database. These requests commit, so every test registers its own
 * user and asserts only on that user's data; nothing depends on seeded counts or on test order. It runs on its own
 * in-memory database so the committed rows never reach the persistence tests, which count the seeded data.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:loanflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class LoanFlowIT {

    private static final String PASSWORD = "12345678";

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    record Reply(int status, JsonNode body, HttpResponse<String> raw) {}

    private Reply call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), BodyHandlers.ofString());
        JsonNode node = response.body().isBlank() ? null : json.readTree(response.body());
        return new Reply(response.statusCode(), node, response);
    }

    private String login(String email, String password) throws Exception {
        Reply reply = call("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertThat(reply.status()).isEqualTo(200);
        return reply.body().get("accessToken").asString();
    }

    private String freshUserToken() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@test.com";
        Reply registered = call("POST", "/api/v1/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Persona Flujo\"}");
        assertThat(registered.status()).isEqualTo(201);
        return login(email, PASSWORD);
    }

    private Reply create(String token, String amount) throws Exception {
        return call("POST", "/api/v1/loans", token, "{\"amount\":" + amount + ",\"termMonths\":12}");
    }

    @Test
    void fullFlow() throws Exception {
        String user = freshUserToken();
        String admin = login("admin@test.com", "123");

        Reply created = create(user, "1500000");
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().get("id").asString();
        assertThat(created.raw().headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/api/v1/loans/" + id));
        assertThat(created.body().get("amount").asString()).isEqualTo("1500000.00");

        Reply mine = call("GET", "/api/v1/me/loans?status=PENDING", user, null);
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.body().get("totalElements").asLong()).isEqualTo(1);
        assertThat(mine.body().get("content").get(0).get("id").asString()).isEqualTo(id);
        assertThat(mine.body().get("content").get(0).get("status").asString()).isEqualTo("PENDING");

        Reply approved = call("POST", "/api/v1/loans/" + id + "/approve", admin, null);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().get("status").asString()).isEqualTo("APPROVED");

        Reply after = call("GET", "/api/v1/loans/" + id, user, null);
        assertThat(after.status()).isEqualTo(200);
        assertThat(after.body().get("status").asString()).isEqualTo("APPROVED");
        assertThat(after.body().get("decidedAt").isNull()).isFalse();
        Reply list = call("GET", "/api/v1/me/loans?status=APPROVED", user, null);
        assertThat(list.body().get("content").get(0).get("status").asString()).isEqualTo("APPROVED");

        Reply decidedAgain = call("POST", "/api/v1/loans/" + id + "/approve", admin, null);
        assertThat(decidedAgain.status()).isEqualTo(409);
        assertThat(decidedAgain.body().get("code").asString()).isEqualTo("LOAN_NOT_PENDING");
    }

    @Test
    void adminListsWithApplicantAndForeignLoanIs404ForOtherUser() throws Exception {
        String user = freshUserToken();
        String other = freshUserToken();
        String admin = login("admin@test.com", "123");
        String id = create(user, "2000").body().get("id").asString();

        assertThat(call("GET", "/api/v1/loans/" + id, other, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/loans/" + id, admin, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/loans", user, null).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/loans/" + id + "/approve", user, null).status()).isEqualTo(403);

        Reply list = call("GET", "/api/v1/loans?applicantEmail=nobody-" + UUID.randomUUID() + "@test.com", admin, null);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get("totalElements").asLong()).isZero();

        Reply all = call("GET", "/api/v1/loans?status=PENDING&size=100&sort=amount,asc", admin, null);
        assertThat(all.status()).isEqualTo(200);
        JsonNode mine = null;
        for (JsonNode row : all.body().get("content")) {
            assertThat(row.get("applicantEmail").asString()).contains("@");
            assertThat(row.get("applicantName").asString()).isNotBlank();
            if (row.get("id").asString().equals(id)) {
                mine = row;
            }
        }
        // The page may not contain it if the seeded DB holds more than 100 pending loans; then it is simply absent.
        if (mine != null) {
            assertThat(mine.get("amount").asString()).isEqualTo("2000.00");
        }
    }

    @Test
    void concurrentApprovalsYieldOneWinner() throws Exception {
        String user = freshUserToken();
        String adminA = login("admin@test.com", "123");
        String adminB = login("admin@test.com", "123");
        String id = create(user, "3000").body().get("id").asString();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Reply>> futures = new ArrayList<>();
            for (String token : List.of(adminA, adminB)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call("POST", "/api/v1/loans/" + id + "/approve", token, null);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Reply> replies = new ArrayList<>();
            for (Future<Reply> future : futures) {
                replies.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(replies).extracting(Reply::status).containsExactlyInAnyOrder(200, 409);
            Reply loser = replies.stream().filter(r -> r.status() == 409).findFirst().orElseThrow();
            assertThat(loser.body().get("code").asString()).isIn("LOAN_NOT_PENDING", "CONCURRENT_MODIFICATION");
        } finally {
            pool.shutdownNow();
        }
        assertThat(call("GET", "/api/v1/loans/" + id, user, null).body().get("status").asString()).isEqualTo("APPROVED");
    }

    @Test
    void fourthPendingIs422() throws Exception {
        String user = freshUserToken();
        for (int i = 0; i < 3; i++) {
            assertThat(create(user, "1000").status()).isEqualTo(201);
        }
        Reply fourth = create(user, "1000");
        assertThat(fourth.status()).isEqualTo(422);
        assertThat(fourth.body().get("code").asString()).isEqualTo("LOAN_POLICY_VIOLATION");
    }

    @Test
    void scientificNotationAmountIs422() throws Exception {
        Reply reply = create(freshUserToken(), "1e10");
        assertThat(reply.status()).isEqualTo(422);
        assertThat(reply.body().get("code").asString()).isEqualTo("LOAN_POLICY_VIOLATION");
    }

    @Test
    void amountShapeErrorsAndPaginationAbuseAre400() throws Exception {
        String user = freshUserToken();
        for (String amount : List.of("100.999", "-5", "0", "\"abc\"", "null", "99999999999999999")) {
            assertThat(create(user, amount).status()).as(amount).isEqualTo(400);
        }
        for (String query : List.of("size=10000", "size=0", "page=-1", "sort=passwordHash", "sort=amount,sideways")) {
            Reply reply = call("GET", "/api/v1/me/loans?" + query, user, null);
            assertThat(reply.status()).as(query).isEqualTo(400);
            assertThat(reply.body().get("code").asString()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void userEditsAndCancelsOwnPendingLoan() throws Exception {
        String user = freshUserToken();
        String admin = login("admin@test.com", "123");
        String id = create(user, "1000").body().get("id").asString();

        Reply edited = call("PUT", "/api/v1/loans/" + id, user, "{\"amount\":\"2500.50\",\"termMonths\":24}");
        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body().get("amount").asString()).isEqualTo("2500.50");
        assertThat(edited.body().get("termMonths").asInt()).isEqualTo(24);

        assertThat(call("DELETE", "/api/v1/loans/" + id, user, null).status()).isEqualTo(204);
        Reply cancelled = call("GET", "/api/v1/loans/" + id, user, null);
        assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");

        assertThat(call("PUT", "/api/v1/loans/" + id, user, "{\"amount\":1000,\"termMonths\":12}").status()).isEqualTo(409);
        assertThat(call("DELETE", "/api/v1/loans/" + id, user, null).status()).isEqualTo(409);
        assertThat(call("POST", "/api/v1/loans/" + id + "/reject", admin, "{\"reason\":\"Ya cancelado\"}").status()).isEqualTo(409);
    }

    @Test
    void rejectStoresReason() throws Exception {
        String user = freshUserToken();
        String admin = login("admin@test.com", "123");
        String id = create(user, "1000").body().get("id").asString();

        assertThat(call("POST", "/api/v1/loans/" + id + "/reject", admin, "{\"reason\":\"   \"}").status()).isEqualTo(400);
        Reply rejected = call("POST", "/api/v1/loans/" + id + "/reject", admin, "{\"reason\":\"Ingresos insuficientes\"}");
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().get("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.body().get("rejectionReason").asString()).isEqualTo("Ingresos insuficientes");
    }
}
