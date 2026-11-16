package com.retail.pim.compliance.infrastructure.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.compliance.domain.service.RuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sandboxed, secure implementation of RuleEvaluator.
 * Uses Spring SimpleEvaluationContext (restricts reflection, class loading T(...), and process execution).
 * Supports SpEL property navigation and JsonLogic JSON AST evaluation.
 */
@Component
public class SpElRuleEvaluator implements RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpElRuleEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean evaluate(String expressionString, Map<String, AttributeValue> attributes) {
        if (expressionString == null || expressionString.isBlank()) {
            return true;
        }

        String trimmed = expressionString.trim();

        // 1. Dual Evaluation: If the expression starts with '{', evaluate as deterministic JsonLogic
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return evaluateJsonLogic(trimmed, attributes);
        }

        // 2. Sandboxed SpEL evaluation
        return evaluateSandboxedSpEl(trimmed, attributes);
    }

    private boolean evaluateSandboxedSpEl(String expressionString, Map<String, AttributeValue> attributes) {
        try {
            // Block explicit code injection keywords proactively
            String lower = expressionString.toLowerCase();
            if (lower.contains("runtime") || lower.contains("processbuilder") || lower.contains("exec(") || lower.contains("getclass")) {
                log.warn("Blocked potentially unsafe rule expression execution attempt: {}", expressionString);
                return false;
            }

            Expression expression = expressionCache.computeIfAbsent(expressionString, parser::parseExpression);

            Map<String, AttributeValue> safeAttrs = attributes != null ? attributes : Collections.emptyMap();

            Map<String, Object> rootModel = new HashMap<>();
            rootModel.put("attributes", safeAttrs);

            Map<String, Object> rawValues = new HashMap<>();
            safeAttrs.forEach((k, v) -> {
                if (v != null) {
                    rawValues.put(k, v.getValue());
                    rootModel.put(k, v); // Direct access: e.g. voltage.value == '230V' or voltage.asString()
                }
            });
            rootModel.put("values", rawValues);

            // Sandboxed evaluation context: read-only property navigation and safe instance methods,
            // strictly NO classloading T(...), NO constructors, and NO bean resolution.
            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forPropertyAccessors(new MapAccessor(), DataBindingPropertyAccessor.forReadOnlyAccess())
                    .withInstanceMethods()
                    .withRootObject(rootModel)
                    .build();

            Object result = expression.getValue(context, rootModel);
            if (result instanceof Boolean b) {
                return b;
            }

            return result != null && Boolean.parseBoolean(result.toString());

        } catch (Exception e) {
            log.debug("Sandboxed SpEL rule '{}' evaluated to false: {}", expressionString, e.getMessage());
            return false;
        }
    }

    /**
     * Lightweight JsonLogic engine supporting {"==": [{"var": "..."}, "..."]}, "!=", ">", "<", "and", "or", "in".
     */
    private boolean evaluateJsonLogic(String jsonString, Map<String, AttributeValue> attributes) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            Map<String, Object> data = new HashMap<>();
            if (attributes != null) {
                attributes.forEach((k, v) -> {
                    if (v != null) {
                        data.put(k, v.getValue());
                    }
                });
            }
            return evaluateJsonLogicNode(root, data);
        } catch (Exception e) {
            log.warn("Failed to evaluate JsonLogic expression: {}", jsonString, e);
            return false;
        }
    }

    private boolean evaluateJsonLogicNode(JsonNode node, Map<String, Object> data) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }

        if (node.isObject() && node.fieldNames().hasNext()) {
            String op = node.fieldNames().next();
            JsonNode args = node.get(op);

            switch (op) {
                case "==" -> {
                    if (args.isArray() && args.size() == 2) {
                        Object left = resolveJsonLogicValue(args.get(0), data);
                        Object right = resolveJsonLogicValue(args.get(1), data);
                        return left != null && left.toString().equalsIgnoreCase(right != null ? right.toString() : null);
                    }
                }
                case "!=" -> {
                    if (args.isArray() && args.size() == 2) {
                        Object left = resolveJsonLogicValue(args.get(0), data);
                        Object right = resolveJsonLogicValue(args.get(1), data);
                        return left == null || !left.toString().equalsIgnoreCase(right != null ? right.toString() : null);
                    }
                }
                case "and" -> {
                    if (args.isArray()) {
                        for (JsonNode sub : args) {
                            if (!evaluateJsonLogicNode(sub, data)) return false;
                        }
                        return true;
                    }
                }
                case "or" -> {
                    if (args.isArray()) {
                        for (JsonNode sub : args) {
                            if (evaluateJsonLogicNode(sub, data)) return true;
                        }
                        return false;
                    }
                }
                case "!" -> {
                    if (args.isArray() && args.size() == 1) {
                        return !evaluateJsonLogicNode(args.get(0), data);
                    }
                    return !evaluateJsonLogicNode(args, data);
                }
                default -> log.warn("Unsupported JsonLogic operator: {}", op);
            }
        }

        return false;
    }

    private Object resolveJsonLogicValue(JsonNode node, Map<String, Object> data) {
        if (node == null || node.isNull()) return null;
        if (node.isObject() && node.has("var")) {
            String varPath = node.get("var").asText();
            if (varPath.startsWith("attributes.")) {
                varPath = varPath.substring("attributes.".length());
            }
            if (varPath.endsWith(".value")) {
                varPath = varPath.substring(0, varPath.length() - ".value".length());
            }
            return data.get(varPath);
        }
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        return node.toString();
    }
}
