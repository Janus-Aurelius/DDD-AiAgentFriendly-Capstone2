package com.retail.pim.compliance.infrastructure.evaluator;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sandboxed SpEL and JsonLogic Security & Functional Tests")
class SandboxedEvaluatorAndJsonLogicTest {

    private SpElRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpElRuleEvaluator();
    }

    @Test
    @DisplayName("Should block malicious code execution attempts in SpEL expressions")
    void shouldBlockMaliciousCodeExecution() {
        Map<String, AttributeValue> attrs = Map.of(
                "voltage", AttributeValue.ofString("230V")
        );

        // Attempts to load Java classes or execute runtime processes must safely evaluate to false
        assertFalse(evaluator.evaluate("T(java.lang.Runtime).getRuntime().exec('calc')", attrs));
        assertFalse(evaluator.evaluate("new java.lang.ProcessBuilder('whoami').start()", attrs));
        assertFalse(evaluator.evaluate("attributes.getClass().getName()", attrs));
    }

    @Test
    @DisplayName("Should evaluate pure JsonLogic expressions securely")
    void shouldEvaluateJsonLogicExpressions() {
        Map<String, AttributeValue> attrs = Map.of(
                "voltage", AttributeValue.ofString("230V"),
                "ce_certified", AttributeValue.ofBoolean(true),
                "energy_rating", AttributeValue.ofString("A++")
        );

        // Simple equality check
        String jsonRule1 = "{\"==\": [{\"var\": \"voltage\"}, \"230V\"]}";
        assertTrue(evaluator.evaluate(jsonRule1, attrs));

        // Inequality / mismatch
        String jsonRule2 = "{\"==\": [{\"var\": \"voltage\"}, \"110V\"]}";
        assertFalse(evaluator.evaluate(jsonRule2, attrs));

        // AND composition
        String jsonAndRule = "{\"and\": [{\"==\": [{\"var\": \"voltage\"}, \"230V\"]}, {\"==\": [{\"var\": \"ce_certified\"}, true]}]}";
        assertTrue(evaluator.evaluate(jsonAndRule, attrs));
    }

    @Test
    @DisplayName("Should evaluate direct attribute access in sandboxed SpEL")
    void shouldEvaluateDirectAttributeAccess() {
        Map<String, AttributeValue> attrs = Map.of(
                "voltage", AttributeValue.ofString("230V")
        );

        assertTrue(evaluator.evaluate("voltage.value == '230V'", attrs));
        assertTrue(evaluator.evaluate("values.voltage == '230V'", attrs));
        assertFalse(evaluator.evaluate("voltage.value == '120V'", attrs));
    }
}
