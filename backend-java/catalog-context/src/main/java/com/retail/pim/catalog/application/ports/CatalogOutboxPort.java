package com.retail.pim.catalog.application.ports;

import com.retail.pim.shared.domain.DomainEvent;

import java.util.Collection;

/**
 * Port for transactional outbox event publishing from the catalog application layer.
 */
public interface CatalogOutboxPort {

    void publishEvents(Collection<DomainEvent> events);
}
