package org.codingburgas.demetra.models.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Periods that end today (inclusive). The previous period is the same number of days right before it, so
 * "+50 %" always compares equal lengths.
 */
public enum RegistrationPeriod {
    TODAY("today", "Днес", "днес", "предходния ден"),
    WEEK("week", "7 дни", "последните 7 дни", "предходните 7 дни"),
    MONTH("month", "Месец", "последния месец", "предходния месец"),
    YEAR("year", "Година", "последната година", "предходната година");

    private final String key;
    private final String label;
    private final String phrase;
    private final String previousPhrase;

    RegistrationPeriod(String key, String label, String phrase, String previousPhrase) {
        this.key = key;
        this.label = label;
        this.phrase = phrase;
        this.previousPhrase = previousPhrase;
    }

    public LocalDate startDate(LocalDate today) {
        return switch (this) {
            case TODAY -> today;
            case WEEK -> today.minusDays(6);
            case MONTH -> today.minusMonths(1).plusDays(1);
            case YEAR -> today.minusYears(1).plusDays(1);
        };
    }

    public long lengthInDays(LocalDate today) {
        return ChronoUnit.DAYS.between(startDate(today), today) + 1;
    }

    /** Unknown or missing keys fall back to today, the first thing the page is asked to show. */
    public static RegistrationPeriod fromKey(String key) {
        for (RegistrationPeriod period : values()) {
            if (period.key.equalsIgnoreCase(key == null ? "" : key.trim())) {
                return period;
            }
        }
        return TODAY;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getPhrase() {
        return phrase;
    }

    public String getPreviousPhrase() {
        return previousPhrase;
    }
}
