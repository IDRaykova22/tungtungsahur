package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.stats.PeriodSummary;
import org.codingburgas.demetra.models.stats.RegistrationPeriod;
import org.codingburgas.demetra.models.stats.RoleShare;
import org.codingburgas.demetra.services.intelligence.Insight;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics.Confidence;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics.Direction;
import org.codingburgas.demetra.services.intelligence.RegistrationInsights;
import org.codingburgas.demetra.services.intelligence.Trend;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The analysis methods are plain functions, so they are tested without Spring or a database. */
class RegistrationAnalyticsTests {
    private final RegistrationAnalytics analytics = new RegistrationAnalytics();
    private final RegistrationInsights insights = new RegistrationInsights(analytics);

    @Test
    void linearTrendFitsAPerfectLine() {
        Trend trend = analytics.linearTrend(new double[]{1, 3, 5, 7, 9});
        assertEquals(2, trend.slope(), 1e-9);
        assertEquals(1, trend.intercept(), 1e-9);
        assertEquals(1, trend.r2(), 1e-9);
        assertEquals(5, trend.mean(), 1e-9);
    }

    @Test
    void flatAndEmptySeriesHaveNoTrend() {
        Trend flat = analytics.linearTrend(new double[]{4, 4, 4, 4});
        assertEquals(0, flat.slope(), 1e-9);
        assertEquals(0, flat.r2(), 1e-9);
        Trend empty = analytics.linearTrend(new double[0]);
        assertEquals(0, empty.slope(), 1e-9);
        assertEquals(Direction.STABLE, analytics.classify(empty, 0));
    }

    @Test
    void forecastContinuesTheLineAndNeverGoesNegative() {
        assertArrayEquals(new double[]{11, 13}, analytics.forecast(new double[]{1, 3, 5, 7, 9}, 2), 1e-9);
        assertArrayEquals(new double[]{0, 0}, analytics.forecast(new double[]{6, 4, 2}, 2), 1e-9);
    }

    @Test
    void anUnreliableTrendIsForecastFromTheAverageInsteadOfTheLine() {
        double[] reliable = {1, 2, 3, 4, 5, 6};
        assertArrayEquals(analytics.forecast(reliable, 3), analytics.adaptiveForecast(reliable, 3), 1e-9);
        // One spike late in a quiet month: a line would predict growth; the average (0.2/day) is the honest guess.
        double[] spike = new double[15];
        spike[10] = 3;
        assertArrayEquals(new double[]{0.2, 0.2}, analytics.adaptiveForecast(spike, 2), 1e-9);
    }

    @Test
    void detectsOnlyClearAndLargeEnoughSpikes() {
        assertEquals(List.of(4), analytics.detectAnomalies(new double[]{1, 1, 2, 1, 9, 1, 1}));
        // One registration after quiet days is not a "spike", whatever its z-score.
        assertEquals(List.of(), analytics.detectAnomalies(new double[]{0, 0, 1, 0, 0, 0}));
        assertEquals(List.of(), analytics.detectAnomalies(new double[]{5, 5, 5, 5, 5}));
        assertEquals(List.of(), analytics.detectAnomalies(new double[]{0, 9, 0}));
    }

    @Test
    void judgesAValueAgainstItsHistory() {
        double[] history = {1, 0, 1, 2, 1, 0, 1, 1};
        assertTrue(analytics.isUnusuallyHigh(6, history));
        assertTrue(!analytics.isUnusuallyHigh(2, history));
        assertTrue(analytics.zScore(6, history) > 4);
    }

    @Test
    void classifiesDirectionAndConfidence() {
        double[] rising = {1, 2, 3, 4, 5, 6};
        double[] falling = {6, 5, 4, 3, 2, 1};
        double[] noisy = {3, 1, 4, 1, 3, 2};
        assertEquals(Direction.RISING, analytics.classify(analytics.linearTrend(rising), rising.length));
        assertEquals(Direction.FALLING, analytics.classify(analytics.linearTrend(falling), falling.length));
        assertEquals(Direction.STABLE, analytics.classify(analytics.linearTrend(noisy), noisy.length));
        assertEquals(Confidence.HIGH, analytics.confidence(analytics.linearTrend(rising), rising));
        assertEquals(Confidence.LOW, analytics.confidence(analytics.linearTrend(noisy), noisy));
        double[] sparse = {0, 0, 5, 0, 0, 1};
        assertEquals(Confidence.LOW, analytics.confidence(analytics.linearTrend(sparse), sparse));
    }

    @Test
    void writesReadableBulgarianConclusions() {
        double[] week = {1, 2, 3, 4, 5, 6, 3};
        List<String> labels = List.of("понеделник, 05.10.2026", "вторник, 06.10.2026", "сряда, 07.10.2026",
                "четвъртък, 08.10.2026", "петък, 09.10.2026", "събота, 10.10.2026", "неделя, 11.10.2026");
        var facts = new RegistrationInsights.Facts(RegistrationPeriod.WEEK,
                new PeriodSummary(RegistrationPeriod.WEEK, 24, 16), new double[]{1, 2, 3, 4, 5, 6}, "ден", week,
                labels, List.of(), 3, 7,
                List.of(new RoleShare(Role.STUDENT, 18, 75), new RoleShare(Role.ORGANIZER, 6, 25),
                        new RoleShare(Role.ADMIN, 0, 0)));

        List<Insight> result = insights.build(facts);
        assertEquals(List.of(Insight.Kind.SUMMARY, Insight.Kind.TREND, Insight.Kind.FORECAST, Insight.Kind.ANOMALY,
                Insight.Kind.PEAK, Insight.Kind.ROLES), result.stream().map(Insight::kind).toList());
        assertEquals("24 регистрации за последните 7 дни — с 50 % повече спрямо предходните 7 дни (16).",
                result.get(0).text());
        assertEquals(Insight.Tone.POSITIVE, result.get(0).tone());
        assertTrue(result.get(1).text().startsWith("Възходяща: с всеки ден идват средно с 1,0 регистрации повече"));
        // Next 7 days after the incomplete today: steps 8–14 of the line 1, 2, 3 … → 8 + 9 + … + 14 = 77.
        assertTrue(result.get(2).text().startsWith("Очакват се около 77 регистрации през следващите 7 дни"),
                result.get(2).text());
        assertTrue(result.get(2).text().contains("увереност: висока"));
        assertEquals("Най-активен ден: събота, 10.10.2026 — 6 регистрации.", result.get(4).text());
        assertEquals("Студенти — 75 %, организатори — 25 %.", result.get(5).text());
    }

    @Test
    void emptyPeriodSaysSoWithoutInventingNumbers() {
        var facts = new RegistrationInsights.Facts(RegistrationPeriod.TODAY,
                new PeriodSummary(RegistrationPeriod.TODAY, 0, 0), new double[29], "ден", new double[24],
                java.util.Collections.nCopies(24, "00:00–01:00"), List.of(), 0, 1,
                List.of(new RoleShare(Role.STUDENT, 0, 0)));
        List<Insight> result = insights.build(facts);
        assertEquals("Няма нови регистрации за днес. Нямаше и в предходния ден.", result.get(0).text());
        assertTrue(result.stream().anyMatch(i -> i.text().startsWith("Недостатъчно данни за прогноза")));
        assertTrue(result.stream().noneMatch(i -> i.kind() == Insight.Kind.ROLES));
    }

    @Test
    void periodsEndTodayAndCompareEqualLengths() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        assertEquals(today, RegistrationPeriod.TODAY.startDate(today));
        assertEquals(LocalDate.of(2026, 10, 6), RegistrationPeriod.WEEK.startDate(today));
        assertEquals(LocalDate.of(2026, 9, 13), RegistrationPeriod.MONTH.startDate(today));
        assertEquals(LocalDate.of(2025, 10, 13), RegistrationPeriod.YEAR.startDate(today));
        assertEquals(30, RegistrationPeriod.MONTH.lengthInDays(today));
        assertEquals(365, RegistrationPeriod.YEAR.lengthInDays(today));
        assertEquals(RegistrationPeriod.TODAY, RegistrationPeriod.fromKey("nonsense"));
        assertEquals(RegistrationPeriod.YEAR, RegistrationPeriod.fromKey(" YEAR "));
    }
}
