package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VariantRevision Domain Model Lifecycle Tests")
class VariantRevisionTest {

    @Test
    @DisplayName("Should create working copy in WORKING_DRAFT state")
    void shouldCreateWorkingCopyInWorkingDraftState() {
        UUID variantId = UUID.randomUUID();
        long baseVersion = 3L;

        VariantRevision revision = VariantRevision.createWorkingCopy(variantId, baseVersion);

        assertNotNull(revision.getId());
        assertEquals(variantId, revision.getVariantId());
        assertEquals(baseVersion, revision.getBaseVersion());
        assertEquals(RevisionState.WORKING_DRAFT, revision.getRevisionState());
        assertTrue(revision.getRemediatedAttributes().isEmpty());
        assertNotNull(revision.getCreatedAt());
        assertNotNull(revision.getUpdatedAt());
    }

    @Test
    @DisplayName("Should update attributes in WORKING_DRAFT state")
    void shouldUpdateAttributesInWorkingDraftState() {
        VariantRevision revision = VariantRevision.createWorkingCopy(UUID.randomUUID(), 1L);
        Map<String, AttributeValue> attrs = Map.of(
                "prop65_warning_label", AttributeValue.ofString("WARNING: Cancer and Reproductive Harm"),
                "prop65_chemical_list", AttributeValue.ofString("Lead, Cadmium")
        );

        revision.updateRemediatedAttributes(attrs);

        assertEquals(2, revision.getRemediatedAttributes().size());
        assertEquals("WARNING: Cancer and Reproductive Harm", revision.getRemediatedAttributes().get("prop65_warning_label").getValue());
    }

    @Test
    @DisplayName("Should enforce state transitions: WORKING_DRAFT -> IN_REVIEW -> APPROVED -> PROMOTED")
    void shouldEnforceHappyPathTransitions() {
        VariantRevision revision = VariantRevision.createWorkingCopy(UUID.randomUUID(), 1L);
        assertEquals(RevisionState.WORKING_DRAFT, revision.getRevisionState());

        revision.submitForReview();
        assertEquals(RevisionState.IN_REVIEW, revision.getRevisionState());

        revision.approve();
        assertEquals(RevisionState.APPROVED, revision.getRevisionState());

        revision.promote();
        assertEquals(RevisionState.PROMOTED, revision.getRevisionState());
    }

    @Test
    @DisplayName("Should handle rejection: WORKING_DRAFT -> IN_REVIEW -> REJECTED")
    void shouldHandleRejectionTransition() {
        VariantRevision revision = VariantRevision.createWorkingCopy(UUID.randomUUID(), 1L);
        revision.submitForReview();
        assertEquals(RevisionState.IN_REVIEW, revision.getRevisionState());

        revision.reject("Missing required chemical list");
        assertEquals(RevisionState.REJECTED, revision.getRevisionState());
    }

    @Test
    @DisplayName("Should reject invalid state transitions")
    void shouldRejectInvalidStateTransitions() {
        VariantRevision revision = VariantRevision.createWorkingCopy(UUID.randomUUID(), 1L);

        // Cannot approve directly from WORKING_DRAFT
        assertThrows(DomainException.class, revision::approve);

        // Cannot promote directly from WORKING_DRAFT
        assertThrows(DomainException.class, revision::promote);

        // Submit for review -> IN_REVIEW
        revision.submitForReview();

        // Cannot edit while in IN_REVIEW
        assertThrows(DomainException.class, () ->
                revision.updateRemediatedAttributes(Map.of("key", AttributeValue.ofString("val"))));

        // Cannot submit again from IN_REVIEW
        assertThrows(DomainException.class, revision::submitForReview);
    }
}
