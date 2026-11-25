package com.wecaelum.prestemos.infrastructure.adapter.in.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.adapter.out.event.LoanEventBus;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** The stream's own lifetime, without Spring: it must end on its own so the front reconnects with a fresh token. */
class LoanEventsControllerTest {

    private static final AuthenticatedUser USER =
            new AuthenticatedUser(UserId.newId(), new Email("usuario@test.com"), Role.USER);

    @Test
    void streamStartsWithConnectedCommentWithoutWaitingForHeartbeat() {
        var controller = new LoanEventsController(
                new LoanEventBus(), new SseProperties(Duration.ofMinutes(10), Duration.ofMinutes(30)));

        StepVerifier.create(controller.events(USER).getBody().take(1))
                .assertNext(event -> assertThat(event.comment()).isEqualTo(LoanEventsController.CONNECTED))
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void streamCompletesAfterMaxDuration() {
        var controller = new LoanEventsController(
                new LoanEventBus(), new SseProperties(Duration.ofMillis(100), Duration.ofMillis(500)));

        var stream = controller.events(USER).getBody();

        assertThat(stream).isNotNull();
        StepVerifier.create(stream)
                .thenConsumeWhile(event -> LoanEventsController.HEARTBEAT.equals(event.comment())
                        || LoanEventsController.CONNECTED.equals(event.comment()))
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }
}
