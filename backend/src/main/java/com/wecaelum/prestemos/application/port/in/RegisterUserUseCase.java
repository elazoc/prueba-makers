package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.User;

public interface RegisterUserUseCase {

    User register(RegisterUserCommand command);
}
