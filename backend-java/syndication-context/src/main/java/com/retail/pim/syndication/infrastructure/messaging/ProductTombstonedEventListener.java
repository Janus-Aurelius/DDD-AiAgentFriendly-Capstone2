package com.retail.pim.syndication.infrastructure.messaging;

import com.retail.pim.catalog.domain.events.ProductTombstonedEvent;
import com.retail.pim.syndication.application.projections.ProductReadModelProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener handling ProductTombstonedEvent to immediately evict decommissioned products
 * from edge search indexes and read-model caches (CUJ 8).
 */
@Component
public class ProductTombstonedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductTombstonedEventListener.class);

    private final ProductReadModelProjector projector;

    public ProductTombstonedEventListener(ProductReadModelProjector projector) {
        this.projector = projector;
    }

    @EventListener
    public void onProductTombstoned(ProductTombstonedEvent event) {
        log.info("Syndication: Ingested ProductTombstonedEvent for SKU [{}] (reason: {}). Purging edge read-model.",
                event.sku(), event.reason());
        projector.purgeProjection(event.sku());
    }
}
