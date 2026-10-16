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
    private static final Locale BG = Locale.forLanguageTag("bg");

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
            String before = s.previousCount() == 0 ? " Нямаше и в " + p.getPreviousPhrase() + "."
                    : " За сравнение, в " + p.getPreviousPhrase() + " — " + registrations(s.previousCount()) + ".";
            return new Insight(Kind.SUMMARY, Tone.NEUTRAL, "Обобщение",
                    "Няма нови регистрации за " + p.getPhrase() + "." + before);
        }
        String text = capitalize(registrations(s.count())) + " за " + p.getPhrase();
        Tone tone = Tone.NEUTRAL;
        if (s.previousCount() == 0) {
            text += ", а в " + p.getPreviousPhrase() + " не е имало нито една.";
            tone = Tone.POSITIVE;
        } else if (s.count() == s.previousCount()) {
            text += " — колкото и в " + p.getPreviousPhrase() + ".";
        } else {
            int change = Math.abs(s.getChangePercent());
            boolean more = s.count() > s.previousCount();
            text += " — с " + change + " % " + (more ? "повече" : "по-малко") + " спрямо " + p.getPreviousPhrase()
                    + " (" + s.previousCount() + ").";
            tone = more ? Tone.POSITIVE : Tone.WARNING;
        }
        return new Insight(Kind.SUMMARY, tone, "Обобщение", text);
    }

    private Insight trend(Facts facts, Trend trend) {
        Direction direction = analytics.classify(trend, facts.history().length);
        String fit = " (R² = " + decimal(trend.r2(), 2) + ")";
        return switch (direction) {
            case RISING -> new Insight(Kind.TREND, Tone.POSITIVE, "Тенденция",
                    "Възходяща: с всеки " + facts.unit() + " идват средно с " + decimal(trend.slope(), 1)
                            + " регистрации повече" + fit + ".");
            case FALLING -> new Insight(Kind.TREND, Tone.WARNING, "Тенденция",
                    "Низходяща: с всеки " + facts.unit() + " идват средно с " + decimal(-trend.slope(), 1)
                            + " регистрации по-малко" + fit + ".");
            case STABLE -> new Insight(Kind.TREND, Tone.NEUTRAL, "Тенденция",
                    "Без ясна тенденция: регистрациите се движат около " + decimal(trend.mean(), 1) + " на "
                            + facts.unit() + fit + ".");
        };
    }

    private Insight forecast(Facts facts, Trend trend) {
        double[] history = facts.history();
        boolean noData = java.util.Arrays.stream(history).allMatch(v -> v == 0);
        if (noData) {
            return new Insight(Kind.FORECAST, Tone.NEUTRAL, "Прогноза",
                    "Недостатъчно данни за прогноза — в историята няма регистрации.");
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
            case TODAY -> "утре";
            case WEEK -> "през следващите 7 дни";
            case MONTH -> "през следващия месец";
            case YEAR -> "през следващите 12 месеца";
        };
        String model = confidence == Confidence.LOW ? "по средното ниво, тенденцията е ненадеждна" : "линейна регресия";
        return new Insight(Kind.FORECAST, Tone.NEUTRAL, "Прогноза",
                "Очакват се около " + registrations(Math.round(total)) + " " + when + " (" + model + ", увереност: "
                        + confidenceLabel(confidence) + ").");
    }

    private Insight anomalies(Facts facts) {
        if (facts.period() == RegistrationPeriod.TODAY) {
            double mean = RegistrationAnalytics.mean(facts.history());
            if (analytics.isUnusuallyHigh(facts.todayCount(), facts.history())) {
                return new Insight(Kind.ANOMALY, Tone.WARNING, "Необичайна активност",
                        "Днес има " + registrations(facts.todayCount()) + " при средно " + decimal(mean, 1)
                                + " на ден за последните " + facts.history().length + " дни (z = "
                                + decimal(analytics.zScore(facts.todayCount(), facts.history()), 1)
                                + "). Проверете дали не са автоматични регистрации.");
            }
            return new Insight(Kind.ANOMALY, Tone.NEUTRAL, "Необичайна активност",
                    "Днешният брой е в обичайните граници (средно " + decimal(mean, 1) + " на ден за последните "
                            + facts.history().length + " дни).");
        }
        if (facts.anomalies().isEmpty()) {
            return new Insight(Kind.ANOMALY, Tone.NEUTRAL, "Необичайна активност",
                    "Няма необичайни пикове — всички стойности са в очакваните граници.");
        }
        double mean = RegistrationAnalytics.mean(facts.chartValues());
        String spikes = facts.anomalies().stream()
                .map(i -> facts.chartLabels().get(i) + " (" + registrations(Math.round(facts.chartValues()[i])) + ")")
                .collect(Collectors.joining(", "));
        return new Insight(Kind.ANOMALY, Tone.WARNING, "Необичайна активност",
                (facts.anomalies().size() == 1 ? "Необичаен пик: " : "Необичайни пикове: ") + spikes
                        + " при средно " + decimal(mean, 1) + ". Отбелязани са в златисто на графиката.");
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
            case TODAY -> "Най-активен час";
            case WEEK, MONTH -> "Най-активен ден";
            case YEAR -> "Най-активен месец";
        };
        return new Insight(Kind.PEAK, Tone.NEUTRAL, "Пик",
                what + ": " + facts.chartLabels().get(best) + " — " + registrations(Math.round(values[best])) + ".");
    }

    private Insight roles(Facts facts) {
        String text = facts.roles().stream()
                .filter(share -> share.count() > 0)
                .map(share -> roleNoun(share.role()) + " — " + share.percent() + " %")
                .collect(Collectors.joining(", "));
        return new Insight(Kind.ROLES, Tone.NEUTRAL, "Роли", capitalize(text) + ".");
    }

    private static String roleNoun(Role role) {
        return switch (role) {
            case STUDENT -> "студенти";
            case ORGANIZER -> "организатори";
            case ADMIN -> "администратори";
        };
    }

    private static String confidenceLabel(Confidence confidence) {
        return switch (confidence) {
            case HIGH -> "висока";
            case MEDIUM -> "средна";
            case LOW -> "ниска";
        };
    }

    /** "1 регистрация", "5 регистрации". */
    public static String registrations(long count) {
        return count + (count == 1 ? " регистрация" : " регистрации");
    }

    static String decimal(double value, int digits) {
        return String.format(BG, "%." + digits + "f", value);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
