package com.wecaelum.prestemos.infrastructure.adapter.out.event;

import com.wecaelum.prestemos.domain.event.LoanEvent;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory bus that feeds the live loan events (design section 11). It hears the domain events only after their
 * transaction commits, so a rolled-back change never reaches a screen. It lives in one JVM: with several
 * instances each one would only see its own events (Redis pub/sub or a broker would be the next step).
 */
@Component
public class LoanEventBus {

    private static final Logger log = LoggerFactory.getLogger(LoanEventBus.class);

    /** Events a subscriber may fall behind by before its oldest pending ones are dropped. */
    static final int SUBSCRIBER_BUFFER = 256;

    /** How long an emitter keeps retrying while another thread is emitting at the same time. */
    private static final Duration CONCURRENT_EMIT_RETRY = Duration.ofMillis(200);

    private final Sinks.Many<LoanEvent> sink = Sinks.many().multicast().directBestEffort();

    /**
     * A hot stream of the committed loan events from the moment of subscribing. Each subscriber drains the sink
     * at once into its own bounded buffer: if the sink saw every subscriber without demand, {@code emitNext}
     * would fail with an overflow and terminate it for everyone, so one stalled client could silence the bus.
     * A subscriber that falls too far behind loses its oldest events; the others are unaffected.
     */
    public Flux<LoanEvent> stream() {
        return sink.asFlux().onBackpressureBuffer(SUBSCRIBER_BUFFER,
                dropped -> log.debug("Slow live-events subscriber; dropped {} {}", dropped.type(), dropped.eventId()),
                BufferOverflowStrategy.DROP_OLDEST);
    }

    /**
     * Emits once the transaction has committed. Two decisions committing at the same time emit from different
     * threads; {@code busyLooping} retries the one that finds the sink busy instead of losing its event.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LoanEvent event) {
        try {
            sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(CONCURRENT_EMIT_RETRY));
        } catch (Sinks.EmissionException e) {
            // The change is already committed: a missed live notice must not turn its response into an error.
            log.warn("Live event {} {} was not delivered: {}", event.type(), event.eventId(), e.getReason());
        }
    }
}
