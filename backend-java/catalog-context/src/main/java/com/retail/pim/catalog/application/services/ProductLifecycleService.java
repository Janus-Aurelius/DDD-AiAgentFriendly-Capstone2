package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.commands.CreateDraftProductCommand;
import com.retail.pim.catalog.application.commands.PublishProductCommand;
import com.retail.pim.catalog.domain.compliance.CategoryCompliancePolicy;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.infrastructure.persistence.OutboxEventPublisher;
import com.retail.pim.catalog.domain.service.PublishabilityGate;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application Orchestration Service: Manages ProductFamily lifecycle transitions.
 * Enforces transactional boundaries between Aggregate Root, PostgreSQL repository,
 * and Transactional Outbox event publishing.
 *
 * Hardened with:
 * 1. Decoupled Category Compliance Policies.
 * 2. Shadow Versioning draft revision lifecycle.
 * 3. Supplier delta merging on re-import.
 * 4. Optimistic locking retry with exponential backoff.
 */
@Service
@Transactional
public class ProductLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ProductLifecycleService.class);

    private final ProductRepository productRepository;
    private final OutboxEventPublisher outboxPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final PublishabilityGate publishabilityGate;
    private final CategoryCompliancePolicy compliancePolicy;

    public ProductLifecycleService(ProductRepository productRepository,
                                  OutboxEventPublisher outboxPublisher,
                                  ApplicationEventPublisher eventPublisher,
                                  @Autowired(required = false) PublishabilityGate publishabilityGate,
                                  CategoryCompliancePolicy compliancePolicy) {
        this.productRepository = productRepository;
        this.outboxPublisher = outboxPublisher;
        this.eventPublisher = eventPublisher;
        this.publishabilityGate = publishabilityGate;
        this.compliancePolicy = compliancePolicy;
    }

    /**
     * Use Case: Instantiates a new ProductFamily aggregate in DRAFT state.
     * Invoked when external VendorItemApprovedForCore Kafka events are received or manual draft is submitted.
     */
    public ProductFamily createDraftProduct(CreateDraftProductCommand command) {
        return executeWithRetry(() -> {
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
        });
    }

    /**
     * Use Case: Transitions aggregate from DRAFT -> ENRICHED.
     * Validates localized marketing descriptions presence.
     */
    public ProductFamily enrichProduct(UUID productId, Map<String, String> localizedDescriptions,
                                       Map<String, AttributeValue> attributes) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

            product.enrich(localizedDescriptions, attributes);

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);
            return saved;
        });
    }

    /**
     * Use Case: Updates attributes on an existing ProductFamily aggregate.
     */
    public ProductFamily updateProductAttributes(UUID productId, Map<String, AttributeValue> attributes) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

            if (attributes != null) {
                attributes.forEach(product::setAttribute);
            }

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);
            return saved;
        });
    }

    /**
     * Use Case: Transitions aggregate from ENRICHED -> APPROVED.
     * Evaluates decoupled regulatory compliance policy and updates variant availability.
     */
    public ProductFamily approveProduct(UUID productId) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

            product.approve(this.compliancePolicy);

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);
            return saved;
        });
    }

    /**
     * Use Case: Transitions aggregate from APPROVED -> PUBLISHED.
     * Enforces primary image on publishable SKUs and writes ProductPublished domain event to Outbox table.
     */
    public ProductFamily publishProduct(PublishProductCommand command) {
        return executeWithRetry(() -> {
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

            product.publish();

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);

            return saved;
        });
    }

    /**
     * Use Case (Shadow Versioning): Starts an in-place draft revision on an already PUBLISHED product.
     * Live storefront listings continue serving the publishedSnapshot without unpublishing!
     */
    public ProductFamily startDraftRevision(UUID productId) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

            product.startDraftRevision();

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);
            return saved;
        });
    }

    /**
     * Use Case (Supplier Re-import / Delta Merge): Merges updated specifications from vendor
     * without overwriting internal merchandising descriptions.
     */
    public ProductFamily mergeSupplierUpdate(String code, Map<String, AttributeValue> attributes, String description) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", code));

            product.mergeSupplierDelta(attributes, description);

            ProductFamily saved = productRepository.save(product);
            dispatchDomainEvents(product);
            return saved;
        });
    }

    /**
     * Use Case: Attaches a physical SKU Variant to an existing ProductFamily aggregate.
     */
    public ProductFamily addVariant(UUID productId, Variant variant) {
        return executeWithRetry(() -> {
            ProductFamily product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductFamily", productId));

            product.addVariant(variant);

            return productRepository.save(product);
        });
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

    private void dispatchDomainEvents(ProductFamily aggregate) {
        List<DomainEvent> events = aggregate.getDomainEvents();
        if (events == null || events.isEmpty()) {
            return;
        }

        if (outboxPublisher != null) {
            outboxPublisher.publishEvents(events);
        }

        if (eventPublisher != null) {
            for (DomainEvent event : events) {
                eventPublisher.publishEvent(event);
            }
        }

        aggregate.clearDomainEvents();
    }

    /**
     * Optimistic Locking Concurrency Mitigation:
     * Retries database operations up to 3 times with exponential backoff
     * when concurrent edits cause version conflicts.
     */
    private <T> T executeWithRetry(Supplier<T> operation) {
        int maxAttempts = 3;
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException ole) {
                log.warn("Optimistic lock conflict detected (attempt {} of {}). Retrying after {}ms...",
                        attempt, maxAttempts, delayMs);
                if (attempt == maxAttempts) {
                    throw ole;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during optimistic locking backoff", ie);
                }
                delayMs *= 2; // exponential backoff
            }
        }
        throw new IllegalStateException("Retry loop terminated unexpectedly");
    }
}
