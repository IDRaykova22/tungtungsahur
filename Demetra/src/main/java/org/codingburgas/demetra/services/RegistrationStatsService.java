package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.stats.ChartBar;
import org.codingburgas.demetra.models.stats.PeriodSummary;
import org.codingburgas.demetra.models.stats.RegistrationDashboard;
import org.codingburgas.demetra.models.stats.RegistrationPeriod;
import org.codingburgas.demetra.models.stats.RoleShare;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.intelligence.RegistrationAnalytics;
import org.codingburgas.demetra.services.intelligence.RegistrationInsights;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** New registrations today and over the last week, month and year, with the automatic analysis. */
@Service
public class RegistrationStatsService {
    private static final Locale BG = Locale.forLanguageTag("bg");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", BG);
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", BG);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLL yy", BG);
    private static final DateTimeFormatter MONTH_LONG = DateTimeFormatter.ofPattern("LLLL yyyy", BG);

    private final UserRepository userRepository;
    private final Clock clock;
    private final RegistrationAnalytics analytics;
    private final RegistrationInsights insights;

    public RegistrationStatsService(UserRepository userRepository, Clock clock, RegistrationAnalytics analytics,
                                    RegistrationInsights insights) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.analytics = analytics;
        this.insights = insights;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public PeriodSummary summarize(RegistrationPeriod period) {
        return summarize(period, today());
    }

    @Transactional(readOnly = true)
    public RegistrationDashboard dashboard(RegistrationPeriod period) {
        LocalDate today = today();
        List<PeriodSummary> summaries = Arrays.stream(RegistrationPeriod.values())
                .map(p -> summarize(p, today)).toList();
        LocalDate start = period.startDate(today);
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        // Today's chart is hourly, but whether today is unusual is judged against the previous 29 days.
        LocalDate loadFrom = period == RegistrationPeriod.TODAY ? today.minusDays(29) : start;
        List<LocalDateTime> timestamps = userRepository.findCreatedAtBetween(loadFrom.atStartOfDay(), end);

        Series chart;
        double[] history;
        String unit = "day";
        int forecastSteps;
        String chartTitle;
        String nextLabel = null;
        switch (period) {
            case TODAY -> {
                chart = hourly(today, timestamps);
                history = daily(today.minusDays(29), today.minusDays(1), timestamps).values();
                forecastSteps = 1;
                chartTitle = "Registrations by hour today";
            }
            case WEEK, MONTH -> {
                chart = daily(start, today, timestamps);
                // Today is not over yet, so it would drag the trend down: the analysis uses complete days only.
                history = Arrays.copyOf(chart.values(), chart.values().length - 1);
                forecastSteps = (int) period.lengthInDays(today);
                chartTitle = "Registrations by day";
                nextLabel = "tomorrow";
            }
            default -> {
                chart = monthly(start, today, timestamps);
                // Only complete months: the current month is unfinished and the first one may be partial.
                int from = start.getDayOfMonth() == 1 ? 0 : 1;
                history = Arrays.copyOfRange(chart.values(), from, chart.values().length - 1);
                unit = "month";
                forecastSteps = 12;
                chartTitle = "Registrations by month";
                nextLabel = today.plusMonths(1).format(MONTH);
            }
        }

        List<Integer> anomalies = analytics.detectAnomalies(chart.values());
        Double next = nextLabel == null ? null : analytics.adaptiveForecast(history, 2)[1];
        List<ChartBar> bars = bars(chart, anomalies, nextLabel, next);
        PeriodSummary selected = summaries.stream().filter(s -> s.period() == period).findFirst().orElseThrow();
        List<RoleShare> roles = roleShares(start.atStartOfDay(), end);
        long todayCount = timestamps.stream().filter(t -> !t.toLocalDate().isBefore(today)).count();

        var facts = new RegistrationInsights.Facts(period, selected, history, unit, chart.values(),
                chart.longLabels(), anomalies, todayCount, forecastSteps, roles);
        return new RegistrationDashboard(period, start, today, summaries, chartTitle, bars, roles,
                insights.build(facts));
    }

    /** The users registered in the period, newest first. */
    @Transactional(readOnly = true)
    public Page<User> newUsers(RegistrationPeriod period, int page, int size) {
        LocalDate today = today();
        return userRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                period.startDate(today).atStartOfDay(), today.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 1) - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    private PeriodSummary summarize(RegistrationPeriod period, LocalDate today) {
        LocalDate start = period.startDate(today);
        LocalDate previousStart = start.minusDays(period.lengthInDays(today));
        long count = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long previous = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                previousStart.atStartOfDay(), start.atStartOfDay());
        return new PeriodSummary(period, count, previous);
    }

    /** Values of the chart with labels for the axis and full names for tooltips and insights. */
    private record Series(List<String> labels, List<String> longLabels, double[] values, List<Boolean> showLabel) {
    }

    private Series hourly(LocalDate day, List<LocalDateTime> timestamps) {
        double[] values = new double[24];
        timestamps.stream().filter(t -> t.toLocalDate().equals(day)).forEach(t -> values[t.getHour()]++);
        List<String> labels = new ArrayList<>();
        List<String> longLabels = new ArrayList<>();
        List<Boolean> show = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format("%02d", hour));
            longLabels.add(String.format("%02d:00-%02d:00", hour, (hour + 1) % 24));
            show.add(hour % 3 == 0);
        }
        return new Series(labels, longLabels, values, show);
    }

    private Series daily(LocalDate from, LocalDate to, List<LocalDateTime> timestamps) {
        int days = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        double[] values = new double[Math.max(days, 0)];
        for (LocalDateTime t : timestamps) {
            long index = t.toLocalDate().toEpochDay() - from.toEpochDay();
            if (index >= 0 && index < days) {
                values[(int) index]++;
            }
        }
        List<String> labels = new ArrayList<>();
        List<String> longLabels = new ArrayList<>();
        List<Boolean> show = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            labels.add(days <= 7 ? date.format(WEEKDAY) + " " + date.format(DAY) : date.format(DAY));
            longLabels.add(date.format(DAY_LONG));
            show.add(days <= 7 || i % 5 == 0 || i == days - 1);
        }
        return new Series(labels, longLabels, values, show);
    }

    private Series monthly(LocalDate from, LocalDate to, List<LocalDateTime> timestamps) {
        YearMonth first = YearMonth.from(from);
        int months = (int) (YearMonth.from(to).getYear() * 12L + YearMonth.from(to).getMonthValue()
                - first.getYear() * 12L - first.getMonthValue()) + 1;
        double[] values = new double[months];
        for (LocalDateTime t : timestamps) {
            YearMonth month = YearMonth.from(t);
            int index = (month.getYear() - first.getYear()) * 12 + month.getMonthValue() - first.getMonthValue();
            if (index >= 0 && index < months) {
                values[index]++;
            }
        }
        List<String> labels = new ArrayList<>();
        List<String> longLabels = new ArrayList<>();
        List<Boolean> show = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth month = first.plusMonths(i);
            labels.add(month.format(MONTH));
            longLabels.add(month.format(MONTH_LONG));
            show.add(true);
        }
        return new Series(labels, longLabels, values, show);
    }

    private List<ChartBar> bars(Series chart, List<Integer> anomalies, String nextLabel, Double next) {
        double max = Arrays.stream(chart.values()).max().orElse(0);
        if (next != null) {
            max = Math.max(max, next);
        }
        List<ChartBar> bars = new ArrayList<>();
        for (int i = 0; i < chart.values().length; i++) {
            double value = chart.values()[i];
            bars.add(new ChartBar(chart.labels().get(i),
                    chart.longLabels().get(i) + ": " + RegistrationInsights.registrations(Math.round(value)),
                    value, height(value, max), anomalies.contains(i), false, chart.showLabel().get(i)));
        }
        if (next != null) {
            bars.add(new ChartBar(nextLabel, "Forecast for " + nextLabel + ": about "
                    + RegistrationInsights.registrations(Math.round(next)), next, height(next, max),
                    false, true, true));
        }
        return bars;
    }

    /** Relative height; any non-zero value stays visible as a thin bar. */
    private static int height(double value, double max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return (int) Math.max(3, Math.round(value * 100 / max));
    }

    /** Percentages by largest remainder, so the shares always add up to exactly 100. */
    private List<RoleShare> roleShares(LocalDateTime start, LocalDateTime end) {
        Map<Role, Long> counts = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            counts.put(role, 0L);
        }
        for (Object[] row : userRepository.countByRoleBetween(start, end)) {
            counts.put((Role) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        int[] percents = new int[Role.values().length];
        double[] remainders = new double[percents.length];
        int assigned = 0;
        for (Role role : Role.values()) {
            double exact = total == 0 ? 0 : counts.get(role) * 100.0 / total;
            percents[role.ordinal()] = (int) Math.floor(exact);
            remainders[role.ordinal()] = exact - Math.floor(exact);
            assigned += percents[role.ordinal()];
        }
        for (int left = total == 0 ? 0 : 100 - assigned; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            percents[best]++;
            remainders[best] = -1;
        }
        List<RoleShare> shares = new ArrayList<>();
        for (Role role : Role.values()) {
            shares.add(new RoleShare(role, counts.get(role), percents[role.ordinal()]));
        }
        return shares;
    }
}
