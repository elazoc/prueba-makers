package com.wecaelum.prestemos.infrastructure.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.wecaelum.prestemos.domain.event.LoanApproved;
import com.wecaelum.prestemos.domain.event.LoanEvent;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.test.StepVerifier;

/** The in-memory bus on its own: fan-out, concurrent emitters and slow subscribers (design section 11.3). */
class LoanEventBusTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final LoanEventBus bus = new LoanEventBus();

    private static LoanEvent approved() {
        return new LoanApproved(UUID.randomUUID(), LoanId.newId(), UserId.newId(), LoanStatus.APPROVED, Instant.now());
    }

    @Test
    void deliversToAllSubscribers() {
        LoanEvent first = approved();
        LoanEvent second = approved();

        // verifyLater subscribes without blocking, so both subscribers are in place before the events go out.
        var verifyA = StepVerifier.create(bus.stream().take(2)).expectNext(first, second).expectComplete().verifyLater();
        var verifyB = StepVerifier.create(bus.stream().take(2)).expectNext(first, second).expectComplete().verifyLater();
        bus.on(first);
        bus.on(second);

        verifyA.verify(TIMEOUT);
        verifyB.verify(TIMEOUT);
    }

    @Test
    void concurrentEmissionsAreNotLost() throws Exception {
        int threads = 8;
        int perThread = 100;
        var verification = StepVerifier.create(bus.stream().take(threads * perThread).count())
                .expectNext((long) threads * perThread)
                .expectComplete()
                .verifyLater();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> emitters = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                emitters.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < perThread; i++) {
                        bus.on(approved());
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> emitter : emitters) {
                emitter.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        verification.verify(TIMEOUT);
    }

    @Test
    void slowSubscriberDoesNotBlockOthers() {
        List<LoanEvent> events = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            events.add(approved());
        }
        StalledSubscriber slow = new StalledSubscriber(1);
        bus.stream().subscribe(slow);
        var fast = StepVerifier.create(bus.stream().take(events.size()))
                .expectNextSequence(events)
                .expectComplete()
                .verifyLater();

        events.forEach(bus::on);

        fast.verify(TIMEOUT);
        assertThat(slow.received).containsExactly(events.getFirst());
        slow.dispose();
    }

    @Test
    void slowSubscriberAloneDoesNotBreakTheBus() {
        // A single client that stops reading must not terminate the shared sink for whoever subscribes next.
        StalledSubscriber slow = new StalledSubscriber(0);
        bus.stream().subscribe(slow);
        for (int i = 0; i < 1_000; i++) {
            bus.on(approved());
        }
        assertThat(slow.failure).isNull();
        slow.dispose();

        LoanEvent later = approved();
        var next = StepVerifier.create(bus.stream().take(1)).expectNext(later).expectComplete().verifyLater();
        bus.on(later);
        next.verify(TIMEOUT);
    }

    @Test
    void emittingWithoutSubscribersIsANoOp() {
        bus.on(approved());

        LoanEvent later = approved();
        var next = StepVerifier.create(bus.stream().take(1)).expectNext(later).expectComplete().verifyLater();
        bus.on(later);
        next.verify(TIMEOUT);
    }

    /** Asks for a fixed number of events up front and never for more, like a client that stopped reading. */
    private static final class StalledSubscriber extends BaseSubscriber<LoanEvent> {

        private final long demand;
        final List<LoanEvent> received = new CopyOnWriteArrayList<>();
        volatile Throwable failure;

        StalledSubscriber(long demand) {
            this.demand = demand;
        }

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            if (demand > 0) {
                request(demand);
            }
        }

        @Override
        protected void hookOnNext(LoanEvent value) {
            received.add(value);
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            failure = throwable;
        }
    }
}
