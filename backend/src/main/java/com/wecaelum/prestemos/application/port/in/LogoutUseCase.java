package com.wecaelum.prestemos.application.port.in;

public interface LogoutUseCase {

    /** Revokes the session family of the token, if any; never fails for a missing or unknown token. */
    void logout(String rawTokenOrNull);
}
