package org.codingburgas.demetra.models.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Periods that end today (inclusive). The previous period is the same number of days right before it, so
 * "+50 %" always compares equal lengths.
 */
public enum RegistrationPeriod {
    TODAY("today", "Today", "today", "the previous day"),
    WEEK("week", "7 days", "the last 7 days", "the previous 7 days"),
    MONTH("month", "Month", "the last month", "the previous month"),
    YEAR("year", "Year", "the last year", "the previous year");

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
