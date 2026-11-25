package com.wecaelum.prestemos;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.adapter.in.sse.LoanEventPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * The live loan events end to end: a real port (a never-ending stream hangs MockMvc), real login tokens and the
 * real transaction boundaries. Commands commit, so the class runs on its own in-memory database and every test
 * filters the stream down to the loans or users it created; a short heartbeat keeps the heartbeat test fast.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:loanevents;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "app.sse.heartbeat=200ms"
        })
class LoanEventsSseIT {

    private static final String PASSWORD = "12345678";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<ServerSentEvent<LoanEventPayload>> SSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    @LocalServerPort
    int port;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    private WebTestClient client;

    record Account(UUID id, String token) {}

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void adminReceivesRequestedEvent() throws Exception {
        Account user = freshUser();
        Flux<ServerSentEvent<LoanEventPayload>> stream = loanEvents(login("admin@test.com", "123"))
                .filter(event -> user.id().equals(event.data().applicantId()));
        var first = stream.take(1).collectList().timeout(TIMEOUT).toFuture();

        UUID loanId = requestLoan(user, "1500000");

        List<ServerSentEvent<LoanEventPayload>> received = first.get(10, TimeUnit.SECONDS);
        assertThat(received).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("loan.requested");
            assertThat(event.id()).isEqualTo(event.data().eventId().toString());
            assertThat(event.data().type()).isEqualTo("loan.requested");
            assertThat(event.data().loanId()).isEqualTo(loanId);
            assertThat(event.data().applicantId()).isEqualTo(user.id());
            assertThat(event.data().status()).isEqualTo(LoanStatus.PENDING);
            assertThat(event.data().occurredAt()).isNotNull();
        });
    }

    @Test
    void userReceivesOnlyOwnDecisions() throws Exception {
        Account a = freshUser();
        Account b = freshUser();
        String admin = login("admin@test.com", "123");
        var first = loanEvents(a.token()).take(1).collectList().timeout(TIMEOUT).toFuture();

        // A's own request, B's request and B's approval must not reach A; A's approval must.
        UUID loanOfA = requestLoan(a, "2000");
        UUID loanOfB = requestLoan(b, "3000");
        approve(admin, loanOfB);
        approve(admin, loanOfA);

        List<ServerSentEvent<LoanEventPayload>> received = first.get(10, TimeUnit.SECONDS);
        assertThat(received).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("loan.approved");
            assertThat(event.data().loanId()).isEqualTo(loanOfA);
            assertThat(event.data().applicantId()).isEqualTo(a.id());
            assertThat(event.data().status()).isEqualTo(LoanStatus.APPROVED);
        });
    }

    @Test
    void noEventAfterRollback() {
        LoanApproved rolledBack = approvedEvent();
        LoanApproved committed = approvedEvent();
        Set<UUID> ours = Set.of(rolledBack.loanId().value(), committed.loanId().value());
        Flux<ServerSentEvent<LoanEventPayload>> stream = loanEvents(login("admin@test.com", "123"))
                .filter(event -> ours.contains(event.data().loanId()));

        StepVerifier.create(stream)
                .then(() -> transactions.executeWithoutResult(status -> {
                    events.publishEvent(rolledBack);
                    status.setRollbackOnly();
                }))
                .expectNoEvent(Duration.ofSeconds(1))
                .then(() -> transactions.executeWithoutResult(status -> events.publishEvent(committed)))
                .assertNext(event -> assertThat(event.data().eventId()).isEqualTo(committed.eventId()))
                .thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    void heartbeatArrives() {
        Flux<ServerSentEvent<LoanEventPayload>> heartbeats = client.get().uri("/api/v1/loans/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("usuario@test.com", "123"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE)
                .getResponseBody()
                .filter(event -> "heartbeat".equals(event.comment()));

        StepVerifier.create(heartbeats.take(1))
                .assertNext(event -> assertThat(event.data()).isNull())
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void streamRequiresToken() {
        client.get().uri("/api/v1/loans/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /** Opens the stream and keeps only the loan events (heartbeats carry no data). */
    private Flux<ServerSentEvent<LoanEventPayload>> loanEvents(String token) {
        return client.get().uri("/api/v1/loans/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                // nginx must pass each event through instead of buffering the response.
                .expectHeader().valueEquals("X-Accel-Buffering", "no")
                .returnResult(SSE)
                .getResponseBody()
                .filter(event -> event.data() != null);
    }

    private static LoanApproved approvedEvent() {
        return new LoanApproved(UUID.randomUUID(), LoanId.newId(), UserId.newId(), LoanStatus.APPROVED, Instant.now());
    }

    private Account freshUser() {
        String email = "sse-" + UUID.randomUUID() + "@test.com";
        Map<String, Object> registered = client.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD, "fullName", "Persona Avisos"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JSON).returnResult().getResponseBody();
        return new Account(UUID.fromString((String) registered.get("id")), login(email, PASSWORD));
    }

    private String login(String email, String password) {
        Map<String, Object> body = client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JSON).returnResult().getResponseBody();
        return (String) body.get("accessToken");
    }

    private UUID requestLoan(Account user, String amount) {
        Map<String, Object> body = client.post().uri("/api/v1/loans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":" + amount + ",\"termMonths\":12}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JSON).returnResult().getResponseBody();
        return UUID.fromString((String) body.get("id"));
    }

    private void approve(String adminToken, UUID loanId) {
        client.post().uri("/api/v1/loans/{id}/approve", loanId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk();
    }
}
