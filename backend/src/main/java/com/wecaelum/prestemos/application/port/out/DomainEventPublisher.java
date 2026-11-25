package com.wecaelum.prestemos.application.port.out;

import com.wecaelum.prestemos.domain.event.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
