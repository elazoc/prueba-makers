package com.wecaelum.prestemos.infrastructure.adapter.in.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Live events settings ({@code app.sse.*}). The heartbeat is an SSE comment sent this often so proxies and
 * browsers keep an idle stream open; it must stay well below the proxy's read timeout. {@code maxDuration} ends
 * every stream after that long, so the front reconnects with a fresh token and a change of role or a
 * deactivation takes effect on the stream too. The async request timeout cannot do this: the heartbeat keeps
 * the request active, so it never fires.
 */
@ConfigurationProperties("app.sse")
public record SseProperties(@DefaultValue("15s") Duration heartbeat, @DefaultValue("30m") Duration maxDuration) {

    public SseProperties {
        requirePositive("app.sse.heartbeat", heartbeat);
        requirePositive("app.sse.max-duration", maxDuration);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
