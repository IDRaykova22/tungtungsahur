package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.ProjectQuestion;
import org.codingburgas.demetra.models.ProjectQuestionStatus;
import org.codingburgas.demetra.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectQuestionRepository extends JpaRepository<ProjectQuestion, Long> {
    @EntityGraph(attributePaths = {"event", "askedBy", "answeredBy"})
    List<ProjectQuestion> findByAskedByOrderByCreatedAtDesc(User askedBy);

    @EntityGraph(attributePaths = {"event", "askedBy", "answeredBy"})
    @Query("select q from ProjectQuestion q where q.event.organizer = :organizer order by q.createdAt desc")
    List<ProjectQuestion> findByEventOrganizerOrderByCreatedAtDesc(@Param("organizer") User organizer);

    @EntityGraph(attributePaths = {"event", "askedBy", "answeredBy"})
    List<ProjectQuestion> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"askedBy", "answeredBy"})
    List<ProjectQuestion> findByEventOrderByCreatedAtAsc(Event event);

    @EntityGraph(attributePaths = {"event", "askedBy"})
    List<ProjectQuestion> findByStatusOrderByCreatedAtAsc(ProjectQuestionStatus status);

    @EntityGraph(attributePaths = {"askedBy", "answeredBy"})
    List<ProjectQuestion> findByEventAndAskedByOrderByCreatedAtAsc(Event event, User askedBy);
}
