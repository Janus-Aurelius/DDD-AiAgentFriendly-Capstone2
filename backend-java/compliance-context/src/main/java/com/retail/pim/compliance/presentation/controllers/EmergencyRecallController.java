package com.retail.pim.compliance.presentation.controllers;

import com.retail.pim.compliance.application.services.EmergencyRecallUseCase;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST endpoint for declaring emergency Class 1 product safety recalls.
 */
@RestController
@RequestMapping("/api/v1/compliance/recalls")
public class EmergencyRecallController {

    private final EmergencyRecallUseCase recallService;

    public EmergencyRecallController(EmergencyRecallUseCase recallService) {
        this.recallService = recallService;
    }

    public record RecallRequestDto(
            @NotBlank(message = "SKU is required")
            String sku,

            @NotBlank(message = "Lot number is required")
            String lotNumber,

            String severity,
            String reason
    ) {}

    public record RecallResponseDto(
            UUID eventId,
            String sku,
            String lotNumber,
            String severity,
            String status,
            Instant timestamp
    ) {}

    @PostMapping
    public ResponseEntity<RecallResponseDto> initiateRecall(@Valid @RequestBody RecallRequestDto request) {
        ProductLotRecallInitiatedEvent event = recallService.initiateRecall(
                request.sku(),
                request.lotNumber(),
                request.severity(),
                request.reason()
        );

        RecallResponseDto response = new RecallResponseDto(
                event.eventId(),
                event.sku(),
                event.lotNumber(),
                event.severity(),
                "RECALL_INITIATED_QUARANTINED",
                event.occurredOn()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
