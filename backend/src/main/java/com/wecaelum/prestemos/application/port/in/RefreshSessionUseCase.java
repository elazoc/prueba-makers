package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.application.model.AuthResult;

public interface RefreshSessionUseCase {

    /** Rotates the presented refresh token and issues a new access token for the user's current role. */
    AuthResult refresh(String rawToken);
}
