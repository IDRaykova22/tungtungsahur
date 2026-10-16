package org.codingburgas.demetra.models.reports;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** Totals for one group of the report (a month, a category, a status or an organizer). */
@Getter
@AllArgsConstructor
public final class ReportGroupRow {
    private final String label;
    private final long eventCount;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
}
