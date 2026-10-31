package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.EventReportRow;
import org.codingburgas.demetra.models.reports.ReportGroupRow;
import org.codingburgas.demetra.models.reports.ReportGrouping;
import org.codingburgas.demetra.models.reports.ReportPeriod;
import org.codingburgas.demetra.repositories.ReportRepository;
import org.codingburgas.demetra.repositories.projections.EventReportProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Service
public class ReportService {
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final Locale BG = Locale.forLanguageTag("bg");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", BG);

    private final ReportRepository reportRepository;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Events created between {@code from} and {@code to} (both inclusive), grouped by the chosen criterion. */
    @Transactional(readOnly = true)
    public EventReport generate(LocalDate from, LocalDate to, ReportGrouping grouping) {
        LocalDate today = today();
        ReportPeriod period = ReportPeriod.between(from, to, today);
        List<EventReportRow> events = reportRepository.findEventReportRows(
                        period.getStartInclusive(), period.getEndExclusive(),
                        List.of(EventStatus.PUBLISHED, EventStatus.CLOSED), RegistrationStatus.CONFIRMED)
                .stream().map(this::toReportRow).toList();

        List<ReportGroupRow> groupRows = switch (grouping) {
            case MONTH -> groupByMonth(period, events);
            case CATEGORY -> group(events, EventReportRow::getCategory, Function.identity(),
                    Comparator.comparing(String::toLowerCase));
            case STATUS -> group(events, EventReportRow::getStatus, status -> label(status),
                    Comparator.comparing(EventStatus::ordinal));
            case ORGANIZER -> groupByOrganizer(events);
        };
        return new EventReport(period, grouping, confirmedCount(events), estimatedAmount(events),
                period.getEndDate().equals(today), events, groupRows);
    }

    /** Every month of the range gets a row, including months without events, so gaps are visible. */
    private List<ReportGroupRow> groupByMonth(ReportPeriod period, List<EventReportRow> events) {
        Map<YearMonth, List<EventReportRow>> byMonth = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(period.getStartDate()); !month.isAfter(YearMonth.from(period.getEndDate()));
             month = month.plusMonths(1)) {
            byMonth.put(month, new ArrayList<>());
        }
        events.forEach(row -> byMonth.get(YearMonth.from(row.getCreatedAt())).add(row));
        List<ReportGroupRow> rows = new ArrayList<>();
        byMonth.forEach((month, monthRows) -> rows.add(toGroupRow(month.format(MONTH_LABEL), monthRows)));
        return rows;
    }

    /** By organizer id (two organizers can share a name), labelled and sorted by name. */
    private List<ReportGroupRow> groupByOrganizer(List<EventReportRow> events) {
        Map<Long, List<EventReportRow>> byOrganizer = new LinkedHashMap<>();
        events.forEach(row -> byOrganizer.computeIfAbsent(row.getOrganizerId(), id -> new ArrayList<>()).add(row));
        return byOrganizer.values().stream()
                .map(rows -> toGroupRow(rows.get(0).getOrganizerName(), rows))
                .sorted(Comparator.comparing(row -> row.getLabel().toLowerCase(BG)))
                .toList();
    }

    private <K> List<ReportGroupRow> group(List<EventReportRow> events, Function<EventReportRow, K> key,
                                           Function<K, String> label, Comparator<K> order) {
        Map<K, List<EventReportRow>> groups = new LinkedHashMap<>();
        events.stream().sorted(Comparator.comparing(key, order))
                .forEach(row -> groups.computeIfAbsent(key.apply(row), k -> new ArrayList<>()).add(row));
        List<ReportGroupRow> rows = new ArrayList<>();
        groups.forEach((k, groupRows) -> rows.add(toGroupRow(label.apply(k), groupRows)));
        return rows;
    }

    private ReportGroupRow toGroupRow(String label, List<EventReportRow> rows) {
        return new ReportGroupRow(label, rows.size(), confirmedCount(rows), estimatedAmount(rows));
    }

    private static String label(EventStatus status) {
        return status == EventStatus.CLOSED ? "Closed" : "Published";
    }

    private EventReportRow toReportRow(EventReportProjection source) {
        // The existing entity stores double. Convert once, then calculate using decimal arithmetic.
        BigDecimal price = BigDecimal.valueOf(source.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = price.multiply(BigDecimal.valueOf(source.getConfirmedCount()));
        String category = source.getCategory() == null || source.getCategory().isBlank()
                ? "Uncategorized" : source.getCategory().trim();
        return new EventReportRow(source.getId(), source.getTitle(), category, source.getStatus(),
                source.getCreatedAt(), source.getStartsAt(), price, source.getConfirmedCount(), amount,
                source.getOrganizerId(), source.getOrganizerName());
    }

    private long confirmedCount(List<EventReportRow> rows) {
        return rows.stream().mapToLong(EventReportRow::getConfirmedCount).sum();
    }

    private BigDecimal estimatedAmount(List<EventReportRow> rows) {
        return rows.stream().map(EventReportRow::getEstimatedAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
    }
}
