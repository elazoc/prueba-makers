package com.wecaelum.prestemos.application.port.out;

import com.wecaelum.prestemos.application.model.IssuedToken;
import com.wecaelum.prestemos.domain.model.User;

public interface TokenIssuer {

    IssuedToken issue(User user);
}
