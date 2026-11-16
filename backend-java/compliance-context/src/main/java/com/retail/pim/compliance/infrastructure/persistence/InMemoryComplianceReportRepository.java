package com.retail.pim.compliance.infrastructure.persistence;

import com.retail.pim.compliance.domain.model.ComplianceReport;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe implementation of ComplianceReportRepository.
 */
@Repository
public class InMemoryComplianceReportRepository implements ComplianceReportRepository {

    private final Map<UUID, ComplianceReport> storage = new ConcurrentHashMap<>();

    @Override
    public ComplianceReport save(ComplianceReport report) {
        if (report == null) {
            throw new IllegalArgumentException("ComplianceReport cannot be null");
        }
        storage.put(report.getId(), report);
        return report;
    }

    @Override
    public Optional<ComplianceReport> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ComplianceReport> findByProductId(UUID productId) {
        if (productId == null) return List.of();
        return storage.values().stream()
                .filter(r -> productId.equals(r.getProductId()))
                .sorted(Comparator.comparing(ComplianceReport::getEvaluatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ComplianceReport> findLatestByProductIdAndTarget(UUID productId, String targetLocale, String targetChannel) {
        if (productId == null) return Optional.empty();
        String loc = targetLocale != null ? targetLocale.toUpperCase() : "GLOBAL";
        String ch = targetChannel != null ? targetChannel.toUpperCase() : "ALL";

        return storage.values().stream()
                .filter(r -> productId.equals(r.getProductId()))
                .filter(r -> loc.equalsIgnoreCase(r.getTargetLocale()))
                .filter(r -> ch.equalsIgnoreCase(r.getTargetChannel()))
                .max(Comparator.comparing(ComplianceReport::getEvaluatedAt));
    }

    @Override
    public List<ComplianceReport> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(ComplianceReport::getEvaluatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByProductId(UUID productId) {
        if (productId != null) {
            storage.entrySet().removeIf(entry -> productId.equals(entry.getValue().getProductId()));
        }
    }
}
