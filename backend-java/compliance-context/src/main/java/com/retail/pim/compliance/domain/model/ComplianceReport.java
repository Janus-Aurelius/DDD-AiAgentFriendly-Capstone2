package com.retail.pim.compliance.domain.model;

import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.taxonomy.domain.model.compliance.RuleSeverity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ComplianceReport Aggregate Root / Read Model:
 * Stores the result of compliance checks per Product + Target Context (Locale / Channel).
 * Distinguishes between BLOCKING hard gates and WARNING quality deductions.
 * Tracks productVersion to eliminate eventual-consistency race conditions.
 */
public class ComplianceReport extends AggregateRoot<UUID> {

    private final UUID productId;
    private final Long productVersion;
    private final String productCode;
    private final String targetLocale;
    private final String targetChannel;
    private final double completenessScore;
    private final String status;
    private final List<ComplianceViolation> violations = new ArrayList<>();
    private final int totalRulesEvaluated;
    private final int passedRulesCount;
    private final int blockingViolationsCount;
    private final int warningViolationsCount;
    private final int infoViolationsCount;
    private final Instant evaluatedAt;

    public ComplianceReport(UUID id, UUID productId, Long productVersion, String productCode,
                            String targetLocale, String targetChannel, double completenessScore,
                            String status, List<ComplianceViolation> violations, int totalRulesEvaluated,
                            int passedRulesCount, int blockingViolationsCount, int warningViolationsCount,
                            int infoViolationsCount, Instant evaluatedAt) {
        super(id != null ? id : UUID.randomUUID());
        this.productId = Objects.requireNonNull(productId, "productId cannot be null");
        this.productVersion = productVersion != null ? productVersion : 0L;
        this.productCode = productCode;
        this.targetLocale = targetLocale != null ? targetLocale.toUpperCase() : "GLOBAL";
        this.targetChannel = targetChannel != null ? targetChannel.toUpperCase() : "ALL";
        this.completenessScore = Math.max(0.0, Math.min(100.0, completenessScore));
        this.status = status;
        if (violations != null) {
            this.violations.addAll(violations);
        }
        this.totalRulesEvaluated = totalRulesEvaluated;
        this.passedRulesCount = passedRulesCount;
        this.blockingViolationsCount = blockingViolationsCount;
        this.warningViolationsCount = warningViolationsCount;
        this.infoViolationsCount = infoViolationsCount;
        this.evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
    }

    /**
     * Factory method: Dynamically calculates severity-weighted completeness score and creates report.
     */
    public static ComplianceReport create(UUID productId, Long productVersion, String productCode,
                                          String targetLocale, String targetChannel, int totalRulesEvaluated,
                                          List<ComplianceViolation> violations) {
        List<ComplianceViolation> safeViolations = violations != null ? violations : Collections.emptyList();

        int blockingCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        for (ComplianceViolation v : safeViolations) {
            if (v.getSeverity() == RuleSeverity.BLOCKING) {
                blockingCount++;
            } else if (v.getSeverity() == RuleSeverity.WARNING) {
                warningCount++;
            } else {
                infoCount++;
            }
        }

        int passed = Math.max(0, totalRulesEvaluated - safeViolations.size());

        double score;
        if (totalRulesEvaluated <= 0) {
            score = 100.0;
        } else {
            // Weighted scoring: BLOCKING = 1.0 unit penalty, WARNING = 0.5 unit penalty, INFO = 0 penalty
            double totalPenaltyUnits = (blockingCount * 1.0) + (warningCount * 0.5);
            double scoreCalc = 100.0 - ((totalPenaltyUnits / totalRulesEvaluated) * 100.0);
            score = Math.round(Math.max(0.0, scoreCalc) * 10.0) / 10.0;
        }

        String status;
        if (blockingCount > 0) {
            status = "ACTION_REQUIRED";
        } else if (warningCount > 0) {
            status = "WARNING";
        } else {
            status = "COMPLIANT";
        }

        return new ComplianceReport(
                UUID.randomUUID(),
                productId,
                productVersion,
                productCode,
                targetLocale,
                targetChannel,
                score,
                status,
                safeViolations,
                totalRulesEvaluated,
                passed,
                blockingCount,
                warningCount,
                infoCount,
                Instant.now()
        );
    }

    public static ComplianceReport create(UUID productId, String productCode, String targetLocale,
                                          String targetChannel, int totalRulesEvaluated,
                                          List<ComplianceViolation> violations) {
        return create(productId, 0L, productCode, targetLocale, targetChannel, totalRulesEvaluated, violations);
    }

    /**
     * The Hard Gate: A product is publishable only if there are ZERO BLOCKING violations.
     */
    public boolean isPublishable() {
        return blockingViolationsCount == 0;
    }

    /**
     * Checks if this report satisfies publishability for the specific target locale and channel.
     */
    public boolean isPublishableFor(String targetLocale, String targetChannel) {
        boolean localeMatch = targetLocale == null || "ALL".equalsIgnoreCase(targetLocale) ||
                this.targetLocale.equalsIgnoreCase(targetLocale) || "GLOBAL".equalsIgnoreCase(this.targetLocale);
        boolean channelMatch = targetChannel == null || "ALL".equalsIgnoreCase(targetChannel) ||
                this.targetChannel.equalsIgnoreCase(targetChannel);

        return localeMatch && channelMatch && isPublishable();
    }

    /**
     * Detects if this report is stale relative to the product's aggregate version.
     */
    public boolean isUpToDateFor(Long currentProductVersion) {
        if (currentProductVersion == null) return true;
        return this.productVersion != null && this.productVersion >= currentProductVersion;
    }

    public UUID getProductId() {
        return productId;
    }

    public Long getProductVersion() {
        return productVersion;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getTargetLocale() {
        return targetLocale;
    }

    public String getTargetChannel() {
        return targetChannel;
    }

    public double getCompletenessScore() {
        return completenessScore;
    }

    public String getStatus() {
        return status;
    }

    public List<ComplianceViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public int getTotalRulesEvaluated() {
        return totalRulesEvaluated;
    }

    public int getPassedRulesCount() {
        return passedRulesCount;
    }

    public int getBlockingViolationsCount() {
        return blockingViolationsCount;
    }

    public int getWarningViolationsCount() {
        return warningViolationsCount;
    }

    public int getInfoViolationsCount() {
        return infoViolationsCount;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public boolean isCompliant() {
        return "COMPLIANT".equalsIgnoreCase(status);
    }
}
