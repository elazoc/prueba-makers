package com.wecaelum.prestemos.application.model;

import java.time.Duration;

/**
 * How long a session family lives from login ({@code ttl}) and for how long a rotated token is rejected without
 * being treated as theft ({@code reuseGrace}).
 */
public record RefreshTokenPolicy(Duration ttl, Duration reuseGrace) {

    public RefreshTokenPolicy {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("The refresh token ttl must be positive.");
        }
        if (reuseGrace == null || reuseGrace.isNegative()) {
            throw new IllegalArgumentException("The refresh token reuse grace must not be negative.");
        }
    }
}
