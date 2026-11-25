package com.wecaelum.prestemos.domain.event;

import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Instant;

public record UserChanged(UserId userId, Instant occurredAt) implements DomainEvent {}
