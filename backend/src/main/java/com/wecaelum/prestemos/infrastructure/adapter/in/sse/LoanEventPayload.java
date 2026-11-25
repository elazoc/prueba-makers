package com.wecaelum.prestemos.infrastructure.adapter.in.sse;

import com.wecaelum.prestemos.domain.event.LoanEvent;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code data} of a live loan event. It only tells the front that something changed; the front asks the API
 * for the details it needs (design section 11.2).
 */
public record LoanEventPayload(
        UUID eventId, String type, UUID loanId, UUID applicantId, LoanStatus status, Instant occurredAt) {

    public static LoanEventPayload from(LoanEvent event) {
        return new LoanEventPayload(event.eventId(), event.type(), event.loanId().value(),
                event.applicantId().value(), event.status(), event.occurredAt());
    }
}
