package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.repositories.projections.EventReportProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportRepository extends Repository<Event, Long> {
    @Query("""
            select e.id as id, e.title as title, e.category as category, e.status as status,
                   e.createdAt as createdAt, e.startsAt as startsAt, e.price as price,
                   o.id as organizerId, o.name as organizerName, count(r.id) as confirmedCount
            from Event e
            join e.organizer o
            left join Registration r on r.event = e and r.status = :registrationStatus
            where e.createdAt >= :start and e.createdAt < :end and e.status in :statuses
            group by e.id, e.title, e.category, e.status, e.createdAt, e.startsAt, e.price, o.id, o.name
            order by e.createdAt asc, e.id asc
            """)
    List<EventReportProjection> findEventReportRows(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") Collection<EventStatus> statuses,
            @Param("registrationStatus") RegistrationStatus registrationStatus);
}
