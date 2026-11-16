package com.retail.pim.compliance.domain.service;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;

import java.util.Map;

/**
 * Domain Service Port: Evaluates deterministic rule expressions against product attributes.
 */
public interface RuleEvaluator {

    /**
     * Evaluates a string-based rule expression against a product's dynamic attributes map.
     *
     * @param expression the rule logic expression (e.g., SpEL or JSONLogic)
     * @param attributes the map of attribute key to AttributeValue
     * @return true if the attributes satisfy the compliance rule; false otherwise
     */
    boolean evaluate(String expression, Map<String, AttributeValue> attributes);
}
