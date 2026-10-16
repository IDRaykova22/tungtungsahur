package org.codingburgas.demetra.models.reports;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.codingburgas.demetra.models.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public final class EventReportRow {
    private final Long id;
    private final String title;
    private final String category;
    private final EventStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime startsAt;
    private final BigDecimal price;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
    private final Long organizerId;
    private final String organizerName;

    public String getStatusLabel() {
        return status == EventStatus.CLOSED ? "Приключило" : "Публикувано";
    }
}
