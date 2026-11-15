package com.retail.pim.localization.domain.valueobjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable Value Object encapsulating localized marketing copy, titles, and legal warnings.
 */
public final class TranslationBlock implements Serializable {

    private final String locale; // e.g. "en_US", "es_MX", "fr_CA"
    private final String localizedTitle;
    private final String localizedDescription;
    private final String safetyWarnings;

    public TranslationBlock(String locale, String localizedTitle, String localizedDescription, String safetyWarnings) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("Locale code cannot be blank (e.g., en_US, fr_CA)");
        }
        if (localizedTitle == null || localizedTitle.isBlank()) {
            throw new IllegalArgumentException("Localized title cannot be blank");
        }
        this.locale = locale.trim().replace("-", "_");
        this.localizedTitle = localizedTitle.trim();
        this.localizedDescription = localizedDescription != null ? localizedDescription.trim() : "";
        this.safetyWarnings = safetyWarnings != null ? safetyWarnings.trim() : "";
    }

    public boolean hasSafetyWarnings() {
        return safetyWarnings != null && !safetyWarnings.isBlank();
    }

    public String getLocale() {
        return locale;
    }

    public String getLocalizedTitle() {
        return localizedTitle;
    }

    public String getLocalizedDescription() {
        return localizedDescription;
    }

    public String getSafetyWarnings() {
        return safetyWarnings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationBlock that = (TranslationBlock) o;
        return Objects.equals(locale, that.locale) &&
               Objects.equals(localizedTitle, that.localizedTitle) &&
               Objects.equals(localizedDescription, that.localizedDescription) &&
               Objects.equals(safetyWarnings, that.safetyWarnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locale, localizedTitle, localizedDescription, safetyWarnings);
    }
}
