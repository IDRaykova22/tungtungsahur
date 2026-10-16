package org.codingburgas.demetra.models.reports;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public final class EventReport {
    private final ReportPeriod period;
    private final ReportGrouping grouping;
    private final long eventCount;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
    /** The range ends today, so later registrations today can still change the numbers. */
    private final boolean todayIncluded;
    private final List<EventReportRow> events;
    private final List<ReportGroupRow> groupRows;

    public EventReport(ReportPeriod period, ReportGrouping grouping, long confirmedCount, BigDecimal estimatedAmount,
                       boolean todayIncluded, List<EventReportRow> events, List<ReportGroupRow> groupRows) {
        this.period = period;
        this.grouping = grouping;
        this.eventCount = events.size();
        this.confirmedCount = confirmedCount;
        this.estimatedAmount = estimatedAmount;
        this.todayIncluded = todayIncluded;
        this.events = List.copyOf(events);
        this.groupRows = List.copyOf(groupRows);
    }

    /** The group's share of the estimated amount, rounded to a whole percent (0 when the total is zero). */
    public int sharePercent(ReportGroupRow row) {
        if (estimatedAmount.signum() == 0) {
            return 0;
        }
        return row.getEstimatedAmount().multiply(BigDecimal.valueOf(100))
                .divide(estimatedAmount, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
