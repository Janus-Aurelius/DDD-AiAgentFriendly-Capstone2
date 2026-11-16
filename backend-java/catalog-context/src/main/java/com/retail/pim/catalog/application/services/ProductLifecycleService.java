package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.commands.CreateDraftProductCommand;
import com.retail.pim.catalog.application.commands.PublishProductCommand;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.infrastructure.persistence.OutboxEventPublisher;
import com.retail.pim.catalog.domain.service.PublishabilityGate;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application Orchestration Service: Manages ProductFamily lifecycle transitions.
 * Enforces transactional boundaries between Aggregate Root, PostgreSQL repository,
 * and Transactional Outbox event publishing.
 */
@Service
@Transactional
public class ProductLifecycleService {

    private final ProductRepository productRepository;
    private final OutboxEventPublisher outboxPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final PublishabilityGate publishabilityGate;

    public ProductLifecycleService(ProductRepository productRepository,
                                  OutboxEventPublisher outboxPublisher,
                                  ApplicationEventPublisher eventPublisher,
                                  @Autowired(required = false) PublishabilityGate publishabilityGate) {
        this.productRepository = productRepository;
        this.outboxPublisher = outboxPublisher;
        this.eventPublisher = eventPublisher;
        this.publishabilityGate = publishabilityGate;
    }

    public ProductLifecycleService(ProductRepository productRepository,
                                  OutboxEventPublisher outboxPublisher,
                                  ApplicationEventPublisher eventPublisher) {
        this(productRepository, outboxPublisher, eventPublisher, null);
    }

    /**
     * Use Case: Instantiates a new ProductFamily aggregate in DRAFT state.
     * Invoked when external VendorItemApprovedForCore Kafka events are received or manual draft is submitted.
     */
    public ProductFamily createDraftProduct(CreateDraftProductCommand command) {
        if (productRepository.existsByCode(command.code())) {
            throw new DomainException(String.format("Product with code '%s' already exists", command.code()));
        }

        ProductFamily draftProduct = ProductFamily.createDraft(
                command.code(),
                command.name(),
                command.description(),
                command.categoryId(),
                command.attributes()
        );

        ProductFamily saved = productRepository.save(draftProduct);
        dispatchDomainEvents(draftProduct);
        return saved;
    }

    /**
     * Use Case: Transitions aggregate from DRAFT -> ENRICHED.
     * Validates localized marketing descriptions presence.
     */
    public ProductFamily enrichProduct(UUID productId, Map<String, String> localizedDescriptions,
                                       Map<String, AttributeValue> attributes) {
        ProductFamily product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

        product.enrich(localizedDescriptions, attributes);

        ProductFamily saved = productRepository.save(product);
        dispatchDomainEvents(product);
        return saved;
    }

    /**
     * Use Case: Updates attributes on an existing ProductFamily aggregate.
     * Emits ProductAttributesUpdatedEvent to trigger Compliance Engine evaluation.
     */
    public ProductFamily updateProductAttributes(UUID productId, Map<String, AttributeValue> attributes) {
        ProductFamily product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

        product.updateAttributes(attributes);

        ProductFamily saved = productRepository.save(product);
        dispatchDomainEvents(product);
        return saved;
    }

    /**
     * Use Case: Transitions aggregate from ENRICHED -> APPROVED.
     * Enforces variant dimensions, weights, and energy_rating compliance.
     */
    public ProductFamily approveProduct(UUID productId) {
        ProductFamily product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

        product.approve();

        return productRepository.save(product);
    }

    /**
     * Use Case: Transitions aggregate from APPROVED -> PUBLISHED.
     * Enforces primary image on all SKUs and writes ProductPublished domain event to Outbox table.
     */
    public ProductFamily publishProduct(PublishProductCommand command) {
        ProductFamily product = productRepository.findById(command.productId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", command.productId()));

        // Hard Gate: Verify that mandatory/blocking compliance rules are satisfied
        if (publishabilityGate != null) {
            String locale = command.targetLocale();
            String channel = command.targetChannel();
            if (!publishabilityGate.canPublish(product, locale, channel)) {
                List<String> blockers = publishabilityGate.getBlockingViolations(product, locale, channel);
                throw new DomainException(String.format(
                        "Cannot publish product '%s' for target [%s / %s]: %d blocking compliance violation(s) present: %s",
                        product.getCode(), locale, channel, blockers.size(), String.join("; ", blockers)
                ));
            }
        }

        // Aggregate executes state machine logic and registers ProductPublished event
        product.publish();

        // 1. Persist published state to PostgreSQL
        ProductFamily saved = productRepository.save(product);

        // 2. Dispatch domain events
        dispatchDomainEvents(product);

        return saved;
    }

    private void dispatchDomainEvents(ProductFamily aggregate) {
        List<DomainEvent> events = aggregate.getDomainEvents();
        if (events == null || events.isEmpty()) {
            return;
        }

        // Write domain events to Outbox table within the same DB transaction
        if (outboxPublisher != null) {
            outboxPublisher.publishEvents(events);
        }

        // Publish to in-process Spring ApplicationEventPublisher
        if (eventPublisher != null) {
            for (DomainEvent event : events) {
                eventPublisher.publishEvent(event);
            }
        }

        // Clear events from aggregate in-memory state
        aggregate.clearDomainEvents();
    }

    /**
     * Use Case: Attaches a physical SKU Variant to an existing ProductFamily aggregate.
     */
    public ProductFamily addVariant(UUID productId, Variant variant) {
        ProductFamily product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

        product.addVariant(variant);

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductFamily getProductById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));
    }

    @Transactional(readOnly = true)
    public List<ProductFamily> listProducts(int page, int size) {
        return productRepository.findAll(page, size);
    }
}
