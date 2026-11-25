package com.wecaelum.prestemos.application.model;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=[PROTECTED], expiresAt=" + expiresAt + "]";
    }
}
