package com.retail.pim.compliance.domain.repository;

import com.retail.pim.compliance.domain.model.ComplianceReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Repository for persisting and querying product ComplianceReports.
 */
public interface ComplianceReportRepository {

    ComplianceReport save(ComplianceReport report);

    Optional<ComplianceReport> findById(UUID id);

    List<ComplianceReport> findByProductId(UUID productId);

    Optional<ComplianceReport> findLatestByProductIdAndTarget(UUID productId, String targetLocale, String targetChannel);

    List<ComplianceReport> findAll();

    void deleteByProductId(UUID productId);
}
