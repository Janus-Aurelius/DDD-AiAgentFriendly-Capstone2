package com.retail.pim.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base abstract class for DDD Aggregate Roots.
 * Encapsulates transactional consistency boundaries and domain event registration.
 *
 * @param <ID> Identity type
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot() {
        super();
    }

    protected AggregateRoot(ID id) {
        super(id);
    }

    /**
     * Registers a domain event triggered by this aggregate root.
     */
    protected void registerEvent(DomainEvent event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }

    /**
     * Returns an unmodifiable list of domain events recorded by this aggregate.
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all recorded domain events, typically after publishing to an Outbox or Message Bus.
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
