package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.application.model.AuthResult;

public interface AuthenticateUseCase {

    AuthResult login(String email, String rawPassword);
}
