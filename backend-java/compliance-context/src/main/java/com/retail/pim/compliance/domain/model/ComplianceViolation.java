package com.retail.pim.compliance.domain.model;

import com.retail.pim.taxonomy.domain.model.compliance.RuleSeverity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a single rule violation in a ComplianceReport.
 * Severity determines whether this violation is a BLOCKING hard gate or a WARNING quality deduction.
 */
public final class ComplianceViolation implements Serializable {

    private final String ruleId;
    private final String message;
    private final RuleSeverity severity;

    public ComplianceViolation(String ruleId, String message, RuleSeverity severity) {
        this.ruleId = ruleId != null ? ruleId.trim() : "UNKNOWN_RULE";
        this.message = message != null ? message.trim() : "";
        this.severity = severity != null ? severity : RuleSeverity.BLOCKING;
    }

    public ComplianceViolation(String ruleId, String message) {
        this(ruleId, message, RuleSeverity.BLOCKING);
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    public RuleSeverity getSeverity() {
        return severity;
    }

    public boolean isBlocking() {
        return severity == RuleSeverity.BLOCKING;
    }

    public boolean isWarning() {
        return severity == RuleSeverity.WARNING;
    }

    public boolean isInfo() {
        return severity == RuleSeverity.INFO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplianceViolation that = (ComplianceViolation) o;
        return Objects.equals(ruleId, that.ruleId) &&
                Objects.equals(message, that.message) &&
                severity == that.severity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, message, severity);
    }

    @Override
    public String toString() {
        return "ComplianceViolation{" +
                "ruleId='" + ruleId + '\'' +
                ", message='" + message + '\'' +
                ", severity=" + severity +
                '}';
    }
}
