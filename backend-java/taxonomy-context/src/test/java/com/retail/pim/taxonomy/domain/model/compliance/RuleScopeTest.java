package com.retail.pim.taxonomy.domain.model.compliance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleScope Value Object Tests")
class RuleScopeTest {

    @Test
    @DisplayName("Should correctly match exact category, locale, and channel")
    void shouldMatchExactScope() {
        RuleScope scope = RuleScope.of(
                List.of("electronics", "appliances"),
                List.of("EU", "UK"),
                List.of("AMAZON", "SHOPIFY")
        );

        assertTrue(scope.matches("electronics", "EU", "AMAZON"));
        assertTrue(scope.matches("appliances", "UK", "SHOPIFY"));

        // Case insensitivity
        assertTrue(scope.matches("ELECTRONICS", "eu", "amazon"));

        // Mismatch
        assertFalse(scope.matches("clothing", "EU", "AMAZON"));
        assertFalse(scope.matches("electronics", "US", "AMAZON"));
        assertFalse(scope.matches("electronics", "EU", "EBAY"));
    }

    @Test
    @DisplayName("Should match wildcard ALL across any dimension")
    void shouldMatchWildcardScope() {
        RuleScope scope = RuleScope.of(
                List.of("electronics"),
                List.of("ALL"),
                List.of("ALL")
        );

        assertTrue(scope.matches("electronics", "US", "AMAZON"));
        assertTrue(scope.matches("electronics", "JP", "WALMART"));
        assertFalse(scope.matches("furniture", "US", "AMAZON"));
    }

    @Test
    @DisplayName("Should generate flattened scope tags")
    void shouldGenerateScopeTags() {
        RuleScope scope = RuleScope.of(
                List.of("electronics"),
                List.of("EU", "US"),
                List.of("ALL")
        );

        List<String> tags = scope.toTags();
        assertTrue(tags.contains("electronics"));
        assertTrue(tags.contains("EU"));
        assertTrue(tags.contains("US"));
        assertTrue(tags.contains("ALL"));
    }
}
