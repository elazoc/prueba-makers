package com.wecaelum.prestemos.infrastructure.adapter.in.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Live events settings ({@code app.sse.*}). The heartbeat is an SSE comment sent this often so proxies and
 * browsers keep an idle stream open; it must stay well below the proxy's read timeout.
 */
@ConfigurationProperties("app.sse")
public record SseProperties(@DefaultValue("15s") Duration heartbeat) {

    public SseProperties {
        if (heartbeat == null || heartbeat.isNegative() || heartbeat.isZero()) {
            throw new IllegalArgumentException("app.sse.heartbeat must be a positive duration");
        }
    }
}
