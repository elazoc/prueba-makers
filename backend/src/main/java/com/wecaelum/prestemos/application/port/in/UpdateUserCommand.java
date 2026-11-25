package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;

public record UpdateUserCommand(UserId id, UserId actingAdmin, String fullName, Role role, boolean active) {}
