package org.codingburgas.demetra.models.stats;

/** Registrations in a period and in the equally long period before it. */
public record PeriodSummary(RegistrationPeriod period, long count, long previousCount) {

    /** Rounded percentage change, or null when there is nothing to compare with. */
    public Integer getChangePercent() {
        if (previousCount == 0) {
            return null;
        }
        return (int) Math.round((count - previousCount) * 100.0 / previousCount);
    }

    /** Badge text: "▲ 50 %", "▼ 20 %", "= 0 %", "ново" (nothing before) or "—" (nothing at all). */
    public String getDeltaLabel() {
        Integer change = getChangePercent();
        if (change == null) {
            return count > 0 ? "ново" : "—";
        }
        if (change == 0) {
            return "= 0 %";
        }
        return (change > 0 ? "▲ " : "▼ ") + Math.abs(change) + " %";
    }

    public String getTrendClass() {
        if (count == previousCount) {
            return "flat";
        }
        return count > previousCount ? "up" : "down";
    }
}
