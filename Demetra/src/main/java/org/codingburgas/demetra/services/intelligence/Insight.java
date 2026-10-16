package org.codingburgas.demetra.services.intelligence;

/** One conclusion of the automatic analysis, written in plain Bulgarian for the dashboard. */
public record Insight(Kind kind, Tone tone, String title, String text) {

    public enum Kind { SUMMARY, TREND, FORECAST, ANOMALY, PEAK, ROLES }

    /** Colour of the card: good news, neutral information or something worth checking. */
    public enum Tone { POSITIVE, NEUTRAL, WARNING }

    public String getToneClass() {
        return tone.name().toLowerCase();
    }
}
