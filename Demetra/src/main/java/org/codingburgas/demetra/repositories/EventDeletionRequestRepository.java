package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.DeletionRequestStatus;
import org.codingburgas.demetra.models.EventDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface EventDeletionRequestRepository extends JpaRepository<EventDeletionRequest, Long> {
    @EntityGraph(attributePaths = {"event", "requestedBy", "reviewedBy"})
    List<EventDeletionRequest> findAllByOrderByCreatedAtDesc();
    boolean existsByEventIdAndStatus(Long eventId, DeletionRequestStatus status);
    java.util.Optional<EventDeletionRequest> findTopByEventIdAndRequestedByIdOrderByCreatedAtDesc(Long eventId, Long requestedById);
}
