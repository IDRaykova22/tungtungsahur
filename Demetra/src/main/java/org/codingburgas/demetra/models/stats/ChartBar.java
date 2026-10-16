package org.codingburgas.demetra.models.stats;

/**
 * One column of the registrations chart.
 *
 * @param heightPercent height relative to the tallest column (0–100)
 * @param anomaly       flagged by the analysis as an unusual spike
 * @param forecast      a predicted, not an actual, value
 * @param showLabel     whether the axis label is printed (dense charts show every few labels)
 */
public record ChartBar(String label, String tooltip, double value, int heightPercent,
                       boolean anomaly, boolean forecast, boolean showLabel) {

    public String getDisplayValue() {
        return forecast ? "~" + Math.round(value) : String.valueOf(Math.round(value));
    }
}
