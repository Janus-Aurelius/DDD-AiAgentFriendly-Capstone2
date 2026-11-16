package com.retail.pim.compliance.infrastructure.gate;

import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.service.PublishabilityGate;
import com.retail.pim.compliance.domain.model.ComplianceReport;
import com.retail.pim.compliance.domain.model.ComplianceViolation;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import com.retail.pim.compliance.domain.service.ComplianceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Hard Gate Implementation: Bridges Core Catalog Lifecycle with Compliance Engine.
 * If asynchronous background evaluation has not settled or is stale relative to the
 * aggregate version, it executes an immediate synchronous read-through evaluation.
 */
@Component
public class CompliancePublishGate implements PublishabilityGate {

    private static final Logger log = LoggerFactory.getLogger(CompliancePublishGate.class);

    private final ComplianceReportRepository reportRepository;
    private final ComplianceEngine complianceEngine;

    public CompliancePublishGate(ComplianceReportRepository reportRepository, ComplianceEngine complianceEngine) {
        this.reportRepository = reportRepository;
        this.complianceEngine = complianceEngine;
    }

    @Override
    public boolean canPublish(ProductFamily product, String targetLocale, String targetChannel) {
        ComplianceReport report = getOrEvaluateReport(product, targetLocale, targetChannel);
        return report.isPublishableFor(targetLocale, targetChannel);
    }

    @Override
    public List<String> getBlockingViolations(ProductFamily product, String targetLocale, String targetChannel) {
        ComplianceReport report = getOrEvaluateReport(product, targetLocale, targetChannel);
        return report.getViolations().stream()
                .filter(ComplianceViolation::isBlocking)
                .map(v -> String.format("[%s] %s", v.getRuleId(), v.getMessage()))
                .toList();
    }

    private ComplianceReport getOrEvaluateReport(ProductFamily product, String targetLocale, String targetChannel) {
        String locale = (targetLocale != null && !targetLocale.isBlank()) ? targetLocale.toUpperCase() : "GLOBAL";
        String channel = (targetChannel != null && !targetChannel.isBlank()) ? targetChannel.toUpperCase() : "ALL";

        Optional<ComplianceReport> opt = reportRepository.findLatestByProductIdAndTarget(product.getId(), locale, channel);

        // If report exists and matches current aggregate version, return it
        if (opt.isPresent() && opt.get().isUpToDateFor(product.getVersion())) {
            return opt.get();
        }

        // Sub-second window mitigation: Execute synchronous read-through evaluation
        log.info("Publish Gate: Missing or stale compliance report for product [{}] (v{}). Executing synchronous read-through evaluation.",
                product.getCode(), product.getVersion());

        List<ComplianceReport> evaluated = complianceEngine.evaluateProduct(
                product.getId(),
                product.getVersion(),
                product.getCategoryId(),
                null,
                product.getAttributes(),
                List.of(locale),
                channel
        );

        return evaluated.stream().findFirst()
                .orElseGet(() -> ComplianceReport.create(product.getId(), product.getVersion(), product.getCode(), locale, channel, 0, List.of()));
    }
}
