package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

/** {@code expiresIn} is in seconds. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static LoginResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=[PROTECTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", user=" + user + "]";
    }
}
