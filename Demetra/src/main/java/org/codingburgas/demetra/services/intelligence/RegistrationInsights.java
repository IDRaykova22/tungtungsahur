package org.codingburgas.demetra.services.intelligence;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.stats.PeriodSummary;
import org.codingburgas.demetra.models.stats.RegistrationPeriod;
import org.codingburgas.demetra.models.stats.RoleShare;
import org.codingburgas.demetra.services.intelligence.Insight.Kind;
import org.codingburgas.demetra.services.intelligence.Insight.Tone;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics.Confidence;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics.Direction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class RegistrationInsights {
    private static final Locale EN = Locale.US;

    private final RegistrationAnalytics analytics;

    public RegistrationInsights(RegistrationAnalytics analytics) {
        this.analytics = analytics;
    }

    public record Facts(RegistrationPeriod period, PeriodSummary summary, double[] history, String unit,
                        double[] chartValues, List<String> chartLabels, List<Integer> anomalies,
                        long todayCount, int forecastSteps, List<RoleShare> roles) {
    }

    public List<Insight> build(Facts facts) {
        List<Insight> insights = new ArrayList<>();
        insights.add(summary(facts));
        Trend trend = analytics.linearTrend(facts.history());
        if (facts.history().length >= 3) {
            insights.add(trend(facts, trend));
        }
        insights.add(forecast(facts, trend));
        insights.add(anomalies(facts));
        if (facts.summary().count() > 0) {
            insights.add(peak(facts));
            insights.add(roles(facts));
        }
        return insights;
    }

    private Insight summary(Facts facts) {
        PeriodSummary s = facts.summary();
        RegistrationPeriod p = facts.period();
        if (s.count() == 0) {
            String before = s.previousCount() == 0 ? " There were none in " + p.getPreviousPhrase() + "."
                    : " For comparison, there were " + registrations(s.previousCount()) + " in " + p.getPreviousPhrase() + ".";
            return new Insight(Kind.SUMMARY, Tone.NEUTRAL, "Summary",
                    "There are no new registrations for " + p.getPhrase() + "." + before);
        }
        String text = capitalize(registrations(s.count())) + " for " + p.getPhrase();
        Tone tone = Tone.NEUTRAL;
        if (s.previousCount() == 0) {
            text += ", while there were none in " + p.getPreviousPhrase() + ".";
            tone = Tone.POSITIVE;
        } else if (s.count() == s.previousCount()) {
            text += " | the same as in " + p.getPreviousPhrase() + ".";
        } else {
            int change = Math.abs(s.getChangePercent());
            boolean more = s.count() > s.previousCount();
            text += " | " + change + " % " + (more ? "more" : "fewer") + " than " + p.getPreviousPhrase()
                    + " (" + s.previousCount() + ").";
            tone = more ? Tone.POSITIVE : Tone.WARNING;
        }
        return new Insight(Kind.SUMMARY, tone, "Summary", text);
    }

    private Insight trend(Facts facts, Trend trend) {
        Direction direction = analytics.classify(trend, facts.history().length);
        String fit = " (R² = " + decimal(trend.r2(), 2) + ")";
        return switch (direction) {
            case RISING -> new Insight(Kind.TREND, Tone.POSITIVE, "Trend",
                    "Rising: each " + facts.unit() + " brings about " + decimal(trend.slope(), 1)
                            + " more registrations on average" + fit + ".");
            case FALLING -> new Insight(Kind.TREND, Tone.WARNING, "Trend",
                    "Falling: each " + facts.unit() + " brings about " + decimal(-trend.slope(), 1)
                            + " fewer registrations on average" + fit + ".");
            case STABLE -> new Insight(Kind.TREND, Tone.NEUTRAL, "Trend",
                    "No clear trend: registrations stay around " + decimal(trend.mean(), 1) + " per "
                            + facts.unit() + fit + ".");
        };
    }

    private Insight forecast(Facts facts, Trend trend) {
        double[] history = facts.history();
        boolean noData = java.util.Arrays.stream(history).allMatch(v -> v == 0);
        if (noData) {
            return new Insight(Kind.FORECAST, Tone.NEUTRAL, "Forecast",
                    "There is not enough data for a forecast | the history contains no registrations.");
        }
        // The history ends one step before the current, incomplete day or month, so the next period starts
        // two steps past its end.
        double[] predicted = analytics.adaptiveForecast(history, facts.forecastSteps() + 1);
        double total = 0;
        for (int i = 1; i < predicted.length; i++) {
            total += predicted[i];
        }
        Confidence confidence = analytics.confidence(trend, history);
        String when = switch (facts.period()) {
            case TODAY -> "tomorrow";
            case WEEK -> "during the next 7 days";
            case MONTH -> "during the next month";
            case YEAR -> "during the next 12 months";
        };
        String model = confidence == Confidence.LOW ? "based on the average level, the trend is unreliable" : "linear regression";
        return new Insight(Kind.FORECAST, Tone.NEUTRAL, "Forecast",
                "About " + registrations(Math.round(total)) + " are expected " + when + " (" + model + ", confidence: "
                        + confidenceLabel(confidence) + ").");
    }

    private Insight anomalies(Facts facts) {
        if (facts.period() == RegistrationPeriod.TODAY) {
            double mean = RegistrationAnalytics.mean(facts.history());
            if (analytics.isUnusuallyHigh(facts.todayCount(), facts.history())) {
                return new Insight(Kind.ANOMALY, Tone.WARNING, "Unusual activity",
                        "Today has " + registrations(facts.todayCount()) + " compared with an average of " + decimal(mean, 1)
                                + " per day over the last " + facts.history().length + " days (z = "
                                + decimal(analytics.zScore(facts.todayCount(), facts.history()), 1)
                                + "). Check whether these could be automated registrations.");
            }
            return new Insight(Kind.ANOMALY, Tone.NEUTRAL, "Unusual activity",
                    "Today's count is within the usual range (average " + decimal(mean, 1) + " per day over the last "
                            + facts.history().length + " days).");
        }
        if (facts.anomalies().isEmpty()) {
            return new Insight(Kind.ANOMALY, Tone.NEUTRAL, "Unusual activity",
                    "There are no unusual peaks | all values are within the expected range.");
        }
        double mean = RegistrationAnalytics.mean(facts.chartValues());
        String spikes = facts.anomalies().stream()
                .map(i -> facts.chartLabels().get(i) + " (" + registrations(Math.round(facts.chartValues()[i])) + ")")
                .collect(Collectors.joining(", "));
        return new Insight(Kind.ANOMALY, Tone.WARNING, "Unusual activity",
                (facts.anomalies().size() == 1 ? "Unusual peak: " : "Unusual peaks: ") + spikes
                        + " compared with an average of " + decimal(mean, 1) + ". They are highlighted in gold on the chart.");
    }

    private Insight peak(Facts facts) {
        double[] values = facts.chartValues();
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        String what = switch (facts.period()) {
            case TODAY -> "Busiest hour";
            case WEEK, MONTH -> "Busiest day";
            case YEAR -> "Busiest month";
        };
        return new Insight(Kind.PEAK, Tone.NEUTRAL, "Peak",
                what + ": " + facts.chartLabels().get(best) + " | " + registrations(Math.round(values[best])) + ".");
    }

    private Insight roles(Facts facts) {
        String text = facts.roles().stream()
                .filter(share -> share.count() > 0)
                .map(share -> roleNoun(share.role()) + " | " + share.percent() + " %")
                .collect(Collectors.joining(", "));
        return new Insight(Kind.ROLES, Tone.NEUTRAL, "Roles", capitalize(text) + ".");
    }

    private static String roleNoun(Role role) {
        return switch (role) {
            case STUDENT -> "students";
            case ORGANIZER -> "organizers";
            case ADMIN -> "administrators";
            case MANAGER -> "managers";
        };
    }

    private static String confidenceLabel(Confidence confidence) {
        return switch (confidence) {
            case HIGH -> "high";
            case MEDIUM -> "medium";
            case LOW -> "low";
        };
    }

    /** "1 registration", "5 registrations". */
    public static String registrations(long count) {
        return count + (count == 1 ? " registration" : " registrations");
    }

    static String decimal(double value, int digits) {
        return String.format(EN, "%." + digits + "f", value);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
