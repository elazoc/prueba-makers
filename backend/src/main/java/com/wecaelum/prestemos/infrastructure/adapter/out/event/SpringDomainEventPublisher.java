package com.wecaelum.prestemos.infrastructure.adapter.out.event;

import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes domain events as Spring application events; listeners decide when to act (e.g. after commit). */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
