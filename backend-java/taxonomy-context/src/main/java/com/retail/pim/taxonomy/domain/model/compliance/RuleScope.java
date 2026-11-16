package com.retail.pim.taxonomy.domain.model.compliance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Value Object representing the multidimensional targeting scope of a ComplianceRule.
 * Rules dynamically stack across Categories, Locales (e.g., US, EU), and Channels (e.g., AMAZON, ALL).
 */
public final class RuleScope implements Serializable {

    private final List<String> categories;
    private final List<String> locales;
    private final List<String> channels;

    public RuleScope(List<String> categories, List<String> locales, List<String> channels) {
        this.categories = normalize(categories, false);
        this.locales = normalize(locales, true);
        this.channels = normalize(channels, true);
    }

    public static RuleScope of(List<String> categories, List<String> locales, List<String> channels) {
        return new RuleScope(categories, locales, channels);
    }

    public static RuleScope global() {
        return new RuleScope(List.of("ALL"), List.of("ALL"), List.of("ALL"));
    }

    /**
     * Determines whether this scope matches the specified category, target locale, and target channel.
     */
    public boolean matches(String category, String locale, String channel) {
        boolean categoryMatches = matchesDimension(this.categories, category, false);
        boolean localeMatches = matchesDimension(this.locales, locale, true);
        boolean channelMatches = matchesDimension(this.channels, channel, true);

        return categoryMatches && localeMatches && channelMatches;
    }

    /**
     * Matches if the category and channel match, and ANY of the provided locales match.
     */
    public boolean matchesAnyLocale(String category, List<String> targetLocales, String channel) {
        boolean categoryMatches = matchesDimension(this.categories, category, false);
        boolean channelMatches = matchesDimension(this.channels, channel, true);

        if (!categoryMatches || !channelMatches) {
            return false;
        }

        if (targetLocales == null || targetLocales.isEmpty()) {
            return true;
        }

        return targetLocales.stream().anyMatch(loc -> matchesDimension(this.locales, loc, true));
    }

    private boolean matchesDimension(List<String> scopeValues, String value, boolean upperCase) {
        if (scopeValues == null || scopeValues.isEmpty() || scopeValues.contains("ALL") || scopeValues.contains("all")) {
            return true;
        }
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return true;
        }
        String normalized = upperCase ? value.trim().toUpperCase() : value.trim().toLowerCase();
        return scopeValues.contains(normalized);
    }

    private static List<String> normalize(List<String> list, boolean upperCase) {
        if (list == null || list.isEmpty()) {
            return List.of("ALL");
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> upperCase ? s.toUpperCase() : s.toLowerCase())
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getLocales() {
        return locales;
    }

    public List<String> getChannels() {
        return channels;
    }

    /**
     * Flattens scope dimensions into a combined list of tags (e.g. ["electronics", "EU", "AMAZON"]).
     */
    public List<String> toTags() {
        List<String> tags = new ArrayList<>();
        tags.addAll(categories);
        tags.addAll(locales);
        tags.addAll(channels);
        return Collections.unmodifiableList(tags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleScope ruleScope = (RuleScope) o;
        return Objects.equals(categories, ruleScope.categories) &&
                Objects.equals(locales, ruleScope.locales) &&
                Objects.equals(channels, ruleScope.channels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categories, locales, channels);
    }

    @Override
    public String toString() {
        return "RuleScope{" +
                "categories=" + categories +
                ", locales=" + locales +
                ", channels=" + channels +
                '}';
    }
}
