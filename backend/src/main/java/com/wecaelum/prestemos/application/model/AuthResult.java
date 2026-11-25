package com.wecaelum.prestemos.application.model;

import com.wecaelum.prestemos.domain.model.User;

public record AuthResult(IssuedToken token, User user) {}
