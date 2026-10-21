package org.codingburgas.demetra.repositories.projections;

import org.codingburgas.demetra.models.EventStatus;

import java.time.LocalDateTime;


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
