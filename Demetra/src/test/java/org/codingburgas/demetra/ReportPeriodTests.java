package org.codingburgas.demetra;

import org.codingburgas.demetra.models.reports.ReportGrouping;
import org.codingburgas.demetra.models.reports.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodTests {
    private final LocalDate today = LocalDate.of(2026, 9, 15);

    @Test
    void bothDatesAreInclusiveAndTheQueryEndIsMidnightAfterTheLastDay() {
        ReportPeriod period = ReportPeriod.between(LocalDate.of(2026, 4, 1), today, today);
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), period.getStartInclusive());
        assertEquals(LocalDateTime.of(2026, 9, 16, 0, 0), period.getEndExclusive());
        assertEquals(168, period.getDays());
    }

    @Test
    void aSingleDayAndALeapFebruaryAreCountedCorrectly() {
        assertEquals(1, ReportPeriod.between(today, today, today).getDays());
        ReportPeriod february = ReportPeriod.between(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), today);
        assertEquals(29, february.getDays());
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), february.getEndExclusive());
    }

    @Test
    void anyPastRangeUpToTwoYearsIsAllowed() {
        assertEquals(366, ReportPeriod.between(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), today).getDays());
        assertEquals(731, ReportPeriod.between(LocalDate.of(2024, 9, 15), today, today).getDays());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-01, 2026-09-16, Крайната дата не може да е в бъдещето.",
            "2026-09-10, 2026-09-01, Началната дата трябва да е преди крайната или същата.",
            "2024-09-14, 2026-09-15, Периодът може да е най-много 2 години (731 дни)."})
    void rejectsFutureReversedAndTooLongRanges(LocalDate from, LocalDate to, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ReportPeriod.between(from, to, today));
        assertEquals(message, error.getMessage());
    }

    @Test
    void rejectsMissingDates() {
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.between(null, today, today));
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.between(today, null, today));
    }

    @ParameterizedTest
    @CsvSource({"month, MONTH", "CATEGORY, CATEGORY", " status , STATUS", "organizer, ORGANIZER",
            "unknown, MONTH", "'', MONTH"})
    void groupingKeysAreReadLeniently(String key, ReportGrouping expected) {
        assertEquals(expected, ReportGrouping.fromKey(key));
    }
}
