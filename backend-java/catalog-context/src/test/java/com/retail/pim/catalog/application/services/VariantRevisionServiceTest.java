package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.ports.CatalogOutboxPort;
import com.retail.pim.catalog.application.ports.ComplianceValidatorPort;
import com.retail.pim.catalog.domain.enums.RestrictionReason;
import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.events.VariantPromotedEvent;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.model.VariantRevision;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.repository.VariantRevisionRepository;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("VariantRevisionService Application Orchestration Tests")
class VariantRevisionServiceTest {

    private VariantRevisionRepository revisionRepository;
    private ProductRepository productRepository;
    private ComplianceValidatorPort complianceValidator;
    private ApplicationEventPublisher eventPublisher;
    private CatalogOutboxPort outboxPublisher;

    private VariantRevisionService service;

    private ProductFamily productFamily;
    private Variant variant;
    private final String sku = "SKU-PROD-001";

    @BeforeEach
    void setUp() {
        revisionRepository = mock(VariantRevisionRepository.class);
        productRepository = mock(ProductRepository.class);
        complianceValidator = mock(ComplianceValidatorPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        outboxPublisher = mock(CatalogOutboxPort.class);

        service = new VariantRevisionService(
                revisionRepository,
                productRepository,
                complianceValidator,
                eventPublisher,
                outboxPublisher
        );

        UUID categoryId = UUID.randomUUID();
        productFamily = ProductFamily.createDraft("PF-001", "Test Family", "Description", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, "cm/kg");
        variant = new Variant(UUID.randomUUID(), sku, "Test Variant", BigDecimal.valueOf(19.99), "USD", dims, Map.of(), List.of(), true);
        productFamily.addVariant(variant);

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(productFamily));
        when(productRepository.findByVariantId(variant.getId())).thenReturn(Optional.of(productFamily));
        when(revisionRepository.save(any(VariantRevision.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Should create working revision successfully when variant is in active state")
    void shouldCreateWorkingRevisionSuccessfully() {
        VariantRevision revision = service.createWorkingRevision(sku);

        assertNotNull(revision);
        assertEquals(variant.getId(), revision.getVariantId());
        assertEquals(RevisionState.WORKING_DRAFT, revision.getRevisionState());
        assertEquals(variant.getPublishedVersion(), revision.getBaseVersion());

        verify(revisionRepository).save(any(VariantRevision.class));
    }

    @Test
    @DisplayName("Should fail to create working revision if variant is in DISCONTINUED state")
    void shouldFailToCreateRevisionWhenDiscontinued() {
        variant.transitionTo(VariantLifecycleState.SKU_DISCONTINUED);

        DomainException ex = assertThrows(DomainException.class, () -> service.createWorkingRevision(sku));
        assertTrue(ex.getMessage().contains("Cannot create working revision"));
    }

    @Test
    @DisplayName("Should update working revision attributes")
    void shouldUpdateWorkingRevisionAttributes() {
        UUID revisionId = UUID.randomUUID();
        VariantRevision revision = VariantRevision.createWorkingCopy(variant.getId(), 1L);
        when(revisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));

        Map<String, AttributeValue> remediatedAttrs = Map.of(
                "prop65_warning_label", AttributeValue.ofString("Cancer warning"),
                "prop65_chemical_list", AttributeValue.ofString("Lead")
        );

        VariantRevision updated = service.updateWorkingRevision(revisionId, remediatedAttrs);

        assertEquals(2, updated.getRemediatedAttributes().size());
        verify(revisionRepository).save(revision);
    }

    @Test
    @DisplayName("Should approve revision on review submission when compliance validator approves")
    void shouldApproveRevisionWhenCompliant() {
        UUID revisionId = UUID.randomUUID();
        VariantRevision revision = VariantRevision.createWorkingCopy(variant.getId(), 1L);
        when(revisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));
        when(complianceValidator.validateVariantRevision(eq(revision.getId()), anyMap())).thenReturn(true);

        VariantRevision result = service.submitForReview(revisionId);

        assertEquals(RevisionState.APPROVED, result.getRevisionState());
        verify(revisionRepository).save(revision);
    }

    @Test
    @DisplayName("Should reject revision on review submission when compliance validator fails")
    void shouldRejectRevisionWhenNonCompliant() {
        UUID revisionId = UUID.randomUUID();
        VariantRevision revision = VariantRevision.createWorkingCopy(variant.getId(), 1L);
        when(revisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));
        when(complianceValidator.validateVariantRevision(eq(revision.getId()), anyMap())).thenReturn(false);

        VariantRevision result = service.submitForReview(revisionId);

        assertEquals(RevisionState.REJECTED, result.getRevisionState());
        verify(revisionRepository).save(revision);
    }

    @Test
    @DisplayName("Should promote revision: merge attributes, clear US_CA mask, bump version, and emit VariantPromotedEvent")
    void shouldPromoteRevisionEndToEnd() {
        UUID revisionId = UUID.randomUUID();
        VariantRevision revision = VariantRevision.createWorkingCopy(variant.getId(), variant.getPublishedVersion());
        revision.updateRemediatedAttributes(Map.of(
                "prop65_warning_label", AttributeValue.ofString("Compliant California Prop 65 Warning")
        ));
        revision.submitForReview();
        revision.approve();

        // Apply California mask to variant initially
        variant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);
        variant.transitionTo(VariantLifecycleState.SKU_RESTRICTED);
        assertFalse(variant.isRegionallyCompliant("US_CA"));
        long initialVersion = variant.getPublishedVersion();

        when(revisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));

        // Promote revision
        VariantRevision promoted = service.promoteRevision(revisionId);

        // Verification:
        // 1. Revision state is PROMOTED
        assertEquals(RevisionState.PROMOTED, promoted.getRevisionState());

        // 2. Attributes merged into variant
        assertTrue(variant.getAttributes().containsKey("prop65_warning_label"));
        assertEquals("Compliant California Prop 65 Warning", variant.getAttributes().get("prop65_warning_label").getValue());

        // 3. California mask cleared
        assertTrue(variant.isRegionallyCompliant("US_CA"));

        // 4. Version incremented
        assertEquals(initialVersion + 1, variant.getPublishedVersion());

        // 5. Variant lifecycle restored to SKU_ACTIVE
        assertEquals(VariantLifecycleState.SKU_ACTIVE, variant.getLifecycleState());

        // 6. ProductFamily saved
        verify(productRepository).save(productFamily);

        // 7. VariantPromotedEvent emitted
        ArgumentCaptor<VariantPromotedEvent> eventCaptor = ArgumentCaptor.forClass(VariantPromotedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(variant.getId(), eventCaptor.getValue().variantId());
        assertEquals(variant.getSku(), eventCaptor.getValue().sku());
        assertEquals(variant.getPublishedVersion(), eventCaptor.getValue().version());

        verify(outboxPublisher).publishEvents(anyList());
    }

    @Test
    @DisplayName("Should throw DomainException when attempting to promote non-approved revision")
    void shouldThrowExceptionWhenPromotingNonApprovedRevision() {
        UUID revisionId = UUID.randomUUID();
        VariantRevision revision = VariantRevision.createWorkingCopy(variant.getId(), 1L);
        when(revisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));

        assertThrows(DomainException.class, () -> service.promoteRevision(revisionId));
    }
}
