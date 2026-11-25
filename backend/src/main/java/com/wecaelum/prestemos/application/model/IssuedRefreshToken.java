package com.wecaelum.prestemos.application.model;

import java.time.Instant;

/** A refresh token handed to the client; {@code expiresAt} is the end of its session family. */
public record IssuedRefreshToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[value=[PROTECTED], expiresAt=" + expiresAt + "]";
    }
}
