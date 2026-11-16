package com.retail.pim.compliance.presentation.rest;

import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.compliance.domain.model.ComplianceReport;
import com.retail.pim.compliance.domain.model.ComplianceViolation;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import com.retail.pim.compliance.domain.service.ComplianceEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API Controller for Compliance Context:
 * Exposes product completeness scores, compliance status, and violation details for UI / Syndication channels.
 */
@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceReportController {

    private final ComplianceReportRepository reportRepository;
    private final ComplianceEngine complianceEngine;
    private final ProductRepository productRepository;

    public ComplianceReportController(ComplianceReportRepository reportRepository,
                                    ComplianceEngine complianceEngine,
                                    @Autowired(required = false) ProductRepository productRepository) {
        this.reportRepository = reportRepository;
        this.complianceEngine = complianceEngine;
        this.productRepository = productRepository;
    }

    public record EvaluateRequest(
            String categoryCode,
            List<String> targetLocales,
            String targetChannel
    ) {}

    public record ComplianceReportResponse(
            UUID id,
            UUID productId,
            Long productVersion,
            String targetLocale,
            String targetChannel,
            double completenessScore,
            String status,
            boolean isPublishable,
            int totalRulesEvaluated,
            int passedRulesCount,
            int blockingViolationsCount,
            int warningViolationsCount,
            List<ViolationDto> violations,
            Instant evaluatedAt
    ) {}

    public record ViolationDto(
            String ruleId,
            String message,
            String severity
    ) {}

    @GetMapping("/reports/product/{productId}")
    public ResponseEntity<List<ComplianceReportResponse>> getReportsByProductId(
            @PathVariable UUID productId,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String channel) {

        if (locale != null) {
            Optional<ComplianceReport> report = reportRepository.findLatestByProductIdAndTarget(productId, locale, channel);
            return report.map(r -> ResponseEntity.ok(List.of(toResponse(r))))
                    .orElse(ResponseEntity.ok(List.of()));
        }

        List<ComplianceReport> reports = reportRepository.findByProductId(productId);
        return ResponseEntity.ok(reports.stream().map(this::toResponse).toList());
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ComplianceReportResponse> getReportById(@PathVariable UUID reportId) {
        return reportRepository.findById(reportId)
                .map(r -> ResponseEntity.ok(toResponse(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/evaluate/{productId}")
    public ResponseEntity<List<ComplianceReportResponse>> triggerEvaluation(
            @PathVariable UUID productId,
            @RequestBody(required = false) EvaluateRequest request) {

        List<String> locales = request != null ? request.targetLocales() : null;
        String channel = request != null ? request.targetChannel() : null;
        String categoryCode = request != null ? request.categoryCode() : null;

        UUID categoryId = null;
        Long productVersion = 0L;
        java.util.Map<String, com.retail.pim.catalog.domain.valueobjects.AttributeValue> attrs = java.util.Map.of();

        if (productRepository != null) {
            Optional<ProductFamily> productOpt = productRepository.findById(productId);
            if (productOpt.isPresent()) {
                ProductFamily product = productOpt.get();
                categoryId = product.getCategoryId();
                productVersion = product.getVersion();
                attrs = product.getAttributes();
            }
        }

        List<ComplianceReport> reports = complianceEngine.evaluateProduct(
                productId,
                productVersion,
                categoryId,
                categoryCode,
                attrs,
                locales,
                channel
        );

        return ResponseEntity.ok(reports.stream().map(this::toResponse).toList());
    }

    private ComplianceReportResponse toResponse(ComplianceReport report) {
        List<ViolationDto> violationDtos = report.getViolations().stream()
                .map(v -> new ViolationDto(v.getRuleId(), v.getMessage(), v.getSeverity().name()))
                .toList();

        return new ComplianceReportResponse(
                report.getId(),
                report.getProductId(),
                report.getProductVersion(),
                report.getTargetLocale(),
                report.getTargetChannel(),
                report.getCompletenessScore(),
                report.getStatus(),
                report.isPublishable(),
                report.getTotalRulesEvaluated(),
                report.getPassedRulesCount(),
                report.getBlockingViolationsCount(),
                report.getWarningViolationsCount(),
                violationDtos,
                report.getEvaluatedAt()
        );
    }
}
