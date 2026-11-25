package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.UserId;
import java.io.Serializable;

/** What authorization needs to know about a user on every request; small enough to cache. */
public record UserAuthSnapshot(UserId id, Role role, boolean active) implements Serializable {}
