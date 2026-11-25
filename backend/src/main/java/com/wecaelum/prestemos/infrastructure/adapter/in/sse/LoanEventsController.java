package com.wecaelum.prestemos.infrastructure.adapter.in.sse;

import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.event.LoanCancelled;
import com.wecaelum.prestemos.domain.event.LoanEvent;
import com.wecaelum.prestemos.domain.event.LoanRejected;
import com.wecaelum.prestemos.domain.event.LoanRequested;
import com.wecaelum.prestemos.domain.event.LoanUpdated;
import com.wecaelum.prestemos.infrastructure.adapter.out.event.LoanEventBus;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Live loan events over SSE, served by Spring MVC as a {@link Flux} (design section 11). An admin sees every loan
 * event; a user sees only the decisions on their own loans. Every stream ends after {@code app.sse.max-duration};
 * the front then reconnects with its current token.
 */
@RestController
@RequestMapping("/api/v1/loans")
@EnableConfigurationProperties(SseProperties.class)
class LoanEventsController {

    private static final Logger log = LoggerFactory.getLogger(LoanEventsController.class);

    static final String HEARTBEAT = "heartbeat";

    /** Tells nginx to pass every event through as it is written instead of buffering the response. */
    static final String NO_PROXY_BUFFERING = "X-Accel-Buffering";

    private final LoanEventBus bus;
    private final Duration heartbeat;
    private final Duration maxDuration;

    LoanEventsController(LoanEventBus bus, SseProperties properties) {
        this.bus = bus;
        this.heartbeat = properties.heartbeat();
        this.maxDuration = properties.maxDuration();
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<Flux<ServerSentEvent<LoanEventPayload>>> events(@AuthenticationPrincipal AuthenticatedUser me) {
        Flux<ServerSentEvent<LoanEventPayload>> loanEvents = bus.stream()
                .filter(event -> visibleTo(me, event))
                .map(LoanEventsController::toServerSentEvent);
        Flux<ServerSentEvent<LoanEventPayload>> heartbeats = Flux.interval(heartbeat)
                .map(tick -> ServerSentEvent.<LoanEventPayload>builder().comment(HEARTBEAT).build());
        Flux<ServerSentEvent<LoanEventPayload>> stream = Flux.merge(loanEvents, heartbeats)
                // Completes cleanly so the token behind this stream is checked again on reconnect; the async
                // request timeout cannot do it because the heartbeat keeps the request active.
                .take(maxDuration)
                // Completing or cancelling (client gone) unsubscribes from the bus and stops the heartbeat.
                .doFinally(signal -> log.debug("Live events stream of user {} ended ({})", me.id().value(), signal));
        return ResponseEntity.ok().header(NO_PROXY_BUFFERING, "no").body(stream);
    }

    /**
     * Admins get everything, so other admins' screens stay current. A user gets only the decisions on their own
     * loans. The switch is exhaustive over the sealed events: a new event type will not compile until it is
     * placed here.
     */
    static boolean visibleTo(AuthenticatedUser me, LoanEvent event) {
        if (me.isAdmin()) {
            return true;
        }
        return switch (event) {
            case LoanApproved approved -> approved.applicantId().equals(me.id());
            case LoanRejected rejected -> rejected.applicantId().equals(me.id());
            case LoanRequested requested -> false;
            case LoanUpdated updated -> false;
            case LoanCancelled cancelled -> false;
        };
    }

    private static ServerSentEvent<LoanEventPayload> toServerSentEvent(LoanEvent event) {
        return ServerSentEvent.builder(LoanEventPayload.from(event))
                .id(event.eventId().toString())
                .event(event.type())
                .build();
    }
}
