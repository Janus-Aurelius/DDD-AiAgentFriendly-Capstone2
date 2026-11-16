package com.retail.pim.compliance.infrastructure.evaluator;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.compliance.domain.service.RuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpElRuleEvaluator Specification Tests")
class RuleEvaluatorTest {

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpElRuleEvaluator();
    }

    @Test
    @DisplayName("Should evaluate reference rule: attributes.voltage != null && attributes.voltage.value == '230V'")
    void shouldEvaluateVoltageRule() {
        String expression = "attributes.voltage != null && attributes.voltage.value == '230V'";

        // 1. Matches 230V
        Map<String, AttributeValue> validAttrs = Map.of(
                "voltage", AttributeValue.ofString("230V")
        );
        assertTrue(evaluator.evaluate(expression, validAttrs));

        // 2. Fails for 110V
        Map<String, AttributeValue> invalidAttrs = Map.of(
                "voltage", AttributeValue.ofString("110V")
        );
        assertFalse(evaluator.evaluate(expression, invalidAttrs));

        // 3. Fails when voltage is missing
        Map<String, AttributeValue> emptyAttrs = Map.of();
        assertFalse(evaluator.evaluate(expression, emptyAttrs));
    }

    @Test
    @DisplayName("Should evaluate map-indexed expression: attributes['energy_rating'] != null")
    void shouldEvaluateMapIndexedEnergyRating() {
        String expression = "attributes['energy_rating'] != null && !attributes['energy_rating'].asString().isBlank()";

        Map<String, AttributeValue> validAttrs = Map.of(
                "energy_rating", AttributeValue.ofString("A+++")
        );
        assertTrue(evaluator.evaluate(expression, validAttrs));

        Map<String, AttributeValue> invalidAttrs = Map.of(
                "energy_rating", AttributeValue.ofString("   ")
        );
        assertFalse(evaluator.evaluate(expression, invalidAttrs));
    }

    @Test
    @DisplayName("Should evaluate boolean attribute check: attributes['ce_certified']?.asBoolean() == true")
    void shouldEvaluateBooleanAttribute() {
        String expression = "attributes['ce_certified'] != null && attributes['ce_certified'].asBoolean() == true";

        Map<String, AttributeValue> certified = Map.of(
                "ce_certified", AttributeValue.ofBoolean(true)
        );
        assertTrue(evaluator.evaluate(expression, certified));

        Map<String, AttributeValue> notCertified = Map.of(
                "ce_certified", AttributeValue.ofBoolean(false)
        );
        assertFalse(evaluator.evaluate(expression, notCertified));
    }

    @Test
    @DisplayName("Should evaluate direct variable access: voltage != null && voltage.value == '230V'")
    void shouldEvaluateDirectVariableAccess() {
        String expression = "voltage != null && voltage.value == '230V'";

        Map<String, AttributeValue> attrs = Map.of(
                "voltage", AttributeValue.ofString("230V")
        );
        assertTrue(evaluator.evaluate(expression, attrs));
    }

    @Test
    @DisplayName("Should handle malformed or erroneous expressions gracefully by returning false")
    void shouldHandleErroneousExpressionGracefully() {
        String badExpression = "attributes.nonExistentMethodOnNull.call()";
        assertFalse(evaluator.evaluate(badExpression, Map.of()));
    }
}
