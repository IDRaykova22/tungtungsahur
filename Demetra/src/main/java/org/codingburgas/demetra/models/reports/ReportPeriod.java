package org.codingburgas.demetra.models.reports;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** A date range chosen by the user: both dates are inclusive; queries use an exclusive end at midnight after it. */
@Getter
public final class ReportPeriod {
    /** Two years, so that a single report stays readable and the query stays fast. */
    public static final long MAX_DAYS = 731;

    private final LocalDate startDate;
    private final LocalDate endDate;

    private ReportPeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static ReportPeriod between(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Choose a start date and an end date.");
        }
        if (to.isAfter(today)) {
            throw new IllegalArgumentException("The end date cannot be in the future.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The start date must be before or equal to the end date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("The period can be at most 2 years (" + MAX_DAYS + " days).");
        }
        return new ReportPeriod(from, to);
    }

    public long getDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public LocalDateTime getStartInclusive() {
        return startDate.atStartOfDay();
    }

    public LocalDateTime getEndExclusive() {
        return endDate.plusDays(1).atStartOfDay();
    }
}
