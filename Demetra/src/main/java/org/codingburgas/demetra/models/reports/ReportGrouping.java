package org.codingburgas.demetra.models.reports;

import java.util.Locale;

/** The criterion the report rows are grouped by; chosen by the user in the filter. */
public enum ReportGrouping {
    MONTH("month", "Month", "by month"),
    CATEGORY("category", "Category", "by category"),
    STATUS("status", "Status", "by status"),
    ORGANIZER("organizer", "Organizer", "by organizer");

    private final String key;
    private final String label;
    private final String phrase;

    ReportGrouping(String key, String label, String phrase) {
        this.key = key;
        this.label = label;
        this.phrase = phrase;
    }

    /** Unknown or missing values fall back to months, the natural grouping for a period. */
    public static ReportGrouping fromKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (ReportGrouping grouping : values()) {
            if (grouping.key.equals(normalized)) {
                return grouping;
            }
        }
        return MONTH;
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
}
