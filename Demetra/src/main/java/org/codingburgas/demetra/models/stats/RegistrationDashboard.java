package org.codingburgas.demetra.models.stats;

import lombok.Getter;
import org.codingburgas.demetra.services.intelligence.Insight;

import java.time.LocalDate;
import java.util.List;

/** Everything the registrations page shows for the selected period, except the paged user list. */
@Getter
public class RegistrationDashboard {
    private final RegistrationPeriod period;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final PeriodSummary selected;
    private final List<PeriodSummary> summaries;
    private final String chartTitle;
    private final List<ChartBar> bars;
    private final List<RoleShare> roleShares;
    private final List<Insight> insights;

    public RegistrationDashboard(RegistrationPeriod period, LocalDate startDate, LocalDate endDate,
                                 List<PeriodSummary> summaries, String chartTitle, List<ChartBar> bars,
                                 List<RoleShare> roleShares, List<Insight> insights) {
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
        this.summaries = List.copyOf(summaries);
        this.selected = summaries.stream().filter(s -> s.period() == period).findFirst().orElseThrow();
        this.chartTitle = chartTitle;
        this.bars = List.copyOf(bars);
        this.roleShares = List.copyOf(roleShares);
        this.insights = List.copyOf(insights);
    }

    public boolean isHasForecast() {
        return bars.stream().anyMatch(ChartBar::forecast);
    }

    public boolean isHasAnomaly() {
        return bars.stream().anyMatch(ChartBar::anomaly);
    }

    /** CSS conic-gradient for the role donut; an empty period shows a plain grey ring. */
    public String getDonutGradient() {
        long total = roleShares.stream().mapToLong(RoleShare::count).sum();
        if (total == 0) {
            return "conic-gradient(var(--chart-empty) 0 100%)";
        }
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        double from = 0;
        for (RoleShare share : roleShares) {
            double to = from + share.count() * 100.0 / total;
            gradient.append(String.format(java.util.Locale.ROOT, "var(--role-%s) %.2f%% %.2f%%, ",
                    share.getCssClass(), from, to));
            from = to;
        }
        return gradient.substring(0, gradient.length() - 2) + ")";
    }
}
