package com.retail.pim.taxonomy.domain.model.compliance;

import com.retail.pim.shared.domain.Entity;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ComplianceRule Entity: Encapsulates rigid, deterministic business and regulatory logic stored as data.
 * Rules are evaluated dynamically against product attributes rather than hardcoded in domain aggregates.
 */
public class ComplianceRule extends Entity<String> {

    private final String ruleId;
    private String name;
    private String description;
    private int priority;
    private RuleSeverity severity;
    private RuleScope scopes;
    private String expression;
    private String errorMessage;
    private boolean active;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private final Instant createdAt;
    private Instant updatedAt;

    public ComplianceRule(String ruleId, String name, String description, int priority,
                          RuleSeverity severity, RuleScope scopes, String expression,
                          String errorMessage, boolean active, Instant effectiveFrom, Instant effectiveTo) {
        super(ruleId);

        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new DomainException("Rule ID cannot be empty");
        }
        if (expression == null || expression.trim().isEmpty()) {
            throw new DomainException("Rule expression cannot be empty");
        }
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new DomainException("Rule error/violation message cannot be empty");
        }

        this.ruleId = ruleId.trim().toUpperCase();
        this.name = name != null ? name.trim() : this.ruleId;
        this.description = description;
        this.priority = priority;
        this.severity = severity != null ? severity : RuleSeverity.BLOCKING;
        this.scopes = scopes != null ? scopes : RuleScope.global();
        this.expression = expression.trim();
        this.errorMessage = errorMessage.trim();
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public ComplianceRule(String ruleId, String name, String description, int priority,
                          RuleSeverity severity, RuleScope scopes, String expression,
                          String errorMessage, boolean active) {
        this(ruleId, name, description, priority, severity, scopes, expression, errorMessage, active, null, null);
    }

    public ComplianceRule(String ruleId, String name, String description, int priority,
                          RuleScope scopes, String expression, String errorMessage, boolean active) {
        this(ruleId, name, description, priority, RuleSeverity.BLOCKING, scopes, expression, errorMessage, active, null, null);
    }

    public ComplianceRule(String ruleId, String name, String description, int priority,
                          RuleScope scopes, String expression, String errorMessage) {
        this(ruleId, name, description, priority, RuleSeverity.BLOCKING, scopes, expression, errorMessage, true, null, null);
    }

    public ComplianceRule(String ruleId, int priority, RuleScope scopes, String expression, String errorMessage) {
        this(ruleId, ruleId, null, priority, RuleSeverity.BLOCKING, scopes, expression, errorMessage, true, null, null);
    }

    public boolean isCurrentlyEffective(Instant now) {
        Instant checkTime = now != null ? now : Instant.now();
        if (effectiveFrom != null && checkTime.isBefore(effectiveFrom)) {
            return false;
        }
        if (effectiveTo != null && checkTime.isAfter(effectiveTo)) {
            return false;
        }
        return true;
    }

    public boolean matches(String category, String locale, String channel) {
        return active && isCurrentlyEffective(Instant.now()) && scopes.matches(category, locale, channel);
    }

    public boolean matchesAny(String category, List<String> locales, String channel) {
        return active && isCurrentlyEffective(Instant.now()) && scopes.matchesAnyLocale(category, locales, channel);
    }

    public boolean matchesHierarchy(List<String> categoryHierarchy, List<String> locales, String channel) {
        if (!active || !isCurrentlyEffective(Instant.now())) {
            return false;
        }
        if (categoryHierarchy == null || categoryHierarchy.isEmpty()) {
            return matchesAny(null, locales, channel);
        }
        return categoryHierarchy.stream().anyMatch(cat -> scopes.matchesAnyLocale(cat, locales, channel));
    }

    public void update(String name, String description, int priority, RuleSeverity severity,
                       RuleScope scopes, String expression, String errorMessage, boolean active) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new DomainException("Rule expression cannot be empty");
        }
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new DomainException("Rule error message cannot be empty");
        }

        this.name = name != null ? name.trim() : this.ruleId;
        this.description = description;
        this.priority = priority;
        if (severity != null) {
            this.severity = severity;
        }
        if (scopes != null) {
            this.scopes = scopes;
        }
        this.expression = expression.trim();
        this.errorMessage = errorMessage.trim();
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public RuleSeverity getSeverity() {
        return severity;
    }

    public boolean isBlocking() {
        return severity == RuleSeverity.BLOCKING;
    }

    public RuleScope getScopes() {
        return scopes;
    }

    public List<String> getScopeTags() {
        return scopes.toTags();
    }

    public String getExpression() {
        return expression;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getViolationMessage() {
        return errorMessage;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplianceRule that = (ComplianceRule) o;
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId);
    }
}
