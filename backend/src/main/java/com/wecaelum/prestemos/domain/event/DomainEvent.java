package com.wecaelum.prestemos.domain.event;

public sealed interface DomainEvent permits LoanEvent, UserChanged {}
