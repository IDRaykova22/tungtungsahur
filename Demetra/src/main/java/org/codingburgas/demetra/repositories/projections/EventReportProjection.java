package org.codingburgas.demetra.repositories.projections;

import org.codingburgas.demetra.models.EventStatus;

import java.time.LocalDateTime;

/** Only the scalar values needed by the report; no lazy entity relationships. */
public interface EventReportProjection {
    Long getId();
    String getTitle();
    String getCategory();
    EventStatus getStatus();
    LocalDateTime getCreatedAt();
    LocalDateTime getStartsAt();
    double getPrice();
    long getConfirmedCount();
    Long getOrganizerId();
    String getOrganizerName();
}
