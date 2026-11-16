package com.retail.pim.taxonomy.presentation.rest;

import com.retail.pim.taxonomy.domain.model.compliance.ComplianceRule;
import com.retail.pim.taxonomy.domain.model.compliance.RuleScope;
import com.retail.pim.taxonomy.domain.model.compliance.RuleSeverity;
import com.retail.pim.taxonomy.domain.repository.ComplianceRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Governance & Taxonomy Context:
 * Enables dynamic registration, updating, and querying of deterministic compliance rules.
 */
@RestController
@RequestMapping("/api/v1/governance/rules")
public class ComplianceRuleController {

    private final ComplianceRuleRepository ruleRepository;

    public ComplianceRuleController(ComplianceRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public record RuleScopeDto(
            List<String> categories,
            List<String> locales,
            List<String> channels
    ) {}

    public record CreateOrUpdateRuleRequest(
            String ruleId,
            String name,
            String description,
            Integer priority,
            RuleSeverity severity,
            RuleScopeDto scopes,
            String expression,
            String violationMessage,
            Boolean active
    ) {}

    public record RuleResponse(
            String ruleId,
            String name,
            String description,
            int priority,
            RuleSeverity severity,
            RuleScopeDto scopes,
            List<String> scopeTags,
            String expression,
            String violationMessage,
            boolean active
    ) {}

    @GetMapping
    public ResponseEntity<List<RuleResponse>> getRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> locales,
            @RequestParam(required = false) String channel) {

        List<ComplianceRule> rules;
        if (category != null || (locales != null && !locales.isEmpty()) || channel != null) {
            rules = ruleRepository.findApplicableRules(category, locales, channel);
        } else {
            rules = ruleRepository.findAll();
        }

        return ResponseEntity.ok(rules.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<RuleResponse> getRuleById(@PathVariable String ruleId) {
        return ruleRepository.findById(ruleId)
                .map(rule -> ResponseEntity.ok(toResponse(rule)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RuleResponse> createOrUpdateRule(@RequestBody CreateOrUpdateRuleRequest request) {
        RuleScope scope = RuleScope.global();
        if (request.scopes() != null) {
            scope = RuleScope.of(
                    request.scopes().categories(),
                    request.scopes().locales(),
                    request.scopes().channels()
            );
        }

        ComplianceRule rule = new ComplianceRule(
                request.ruleId(),
                request.name(),
                request.description(),
                request.priority() != null ? request.priority() : 100,
                request.severity() != null ? request.severity() : RuleSeverity.BLOCKING,
                scope,
                request.expression(),
                request.violationMessage(),
                request.active() != null ? request.active() : true
        );

        ComplianceRule saved = ruleRepository.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        if (!ruleRepository.existsById(ruleId)) {
            return ResponseEntity.notFound().build();
        }
        ruleRepository.deleteById(ruleId);
        return ResponseEntity.noContent().build();
    }

    private RuleResponse toResponse(ComplianceRule rule) {
        RuleScopeDto scopeDto = new RuleScopeDto(
                rule.getScopes().getCategories(),
                rule.getScopes().getLocales(),
                rule.getScopes().getChannels()
        );

        return new RuleResponse(
                rule.getRuleId(),
                rule.getName(),
                rule.getDescription(),
                rule.getPriority(),
                rule.getSeverity(),
                scopeDto,
                rule.getScopeTags(),
                rule.getExpression(),
                rule.getViolationMessage(),
                rule.isActive()
        );
    }
}
