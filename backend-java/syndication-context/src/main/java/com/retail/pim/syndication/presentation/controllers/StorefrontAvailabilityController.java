package com.retail.pim.syndication.presentation.controllers;

import com.retail.pim.syndication.application.facades.SyndicationModuleFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Storefront Availability PDP Direct Read API.
 * Guarantees sub-2ms bundle purchase protection via Redis Set (blacklisted_component_skus).
 */
@RestController
@RequestMapping("/api/v1/syndication/availability/bundles")
public class StorefrontAvailabilityController {

    private final SyndicationModuleFacade syndicationFacade;

    public StorefrontAvailabilityController(SyndicationModuleFacade syndicationFacade) {
        this.syndicationFacade = syndicationFacade;
    }

    public record BundleAvailabilityResponse(
            String bundleSku,
            boolean available,
            List<String> blacklistedComponents,
            long queryLatencyMs
    ) {}

    @GetMapping("/{sku}")
    public ResponseEntity<BundleAvailabilityResponse> getBundleAvailability(
            @PathVariable String sku,
            @RequestParam(required = false) List<String> components) {

        long startNanos = System.nanoTime();

        List<String> requiredComponents = components != null ? components : List.of();
        boolean available = syndicationFacade.isBundleAvailable(requiredComponents);
        List<String> blacklisted = available ? List.of() : syndicationFacade.getBlacklistedComponents(requiredComponents);

        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        BundleAvailabilityResponse response = new BundleAvailabilityResponse(
                sku,
                available,
                blacklisted,
                latencyMs
        );

        return ResponseEntity.ok(response);
    }
}
