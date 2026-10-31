package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.DeletionRequestStatus;
import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventDeletionRequest;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventDeletionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventDeletionRequestService {
    private final EventDeletionRequestRepository requestRepository;
    private final VideoStorageService videoStorageService;

    public EventDeletionRequestService(EventDeletionRequestRepository requestRepository,
                                       VideoStorageService videoStorageService) {
        this.requestRepository = requestRepository;
        this.videoStorageService = videoStorageService;
    }

    @Transactional
    public EventDeletionRequest request(Event event, User manager, String reason) {
        if (manager == null || manager.getRole() != Role.MANAGER) {
            throw new SecurityException("Only a manager can submit a deletion request.");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("A deletion reason is required.");
        }
        if (requestRepository.existsByEventIdAndStatus(event.getId(), DeletionRequestStatus.PENDING)) {
            throw new IllegalStateException("A request has already been submitted for this event.");
        }
        EventDeletionRequest request = new EventDeletionRequest();
        request.setEvent(event);
        request.setRequestedBy(manager);
        request.setReason(reason.trim());
        request.setStatus(DeletionRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    public List<EventDeletionRequest> list() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    public java.util.Optional<EventDeletionRequest> latestFor(Event event, User manager) {
        return requestRepository.findTopByEventIdAndRequestedByIdOrderByCreatedAtDesc(event.getId(), manager.getId());
    }

    @Transactional
    public EventDeletionRequest approve(Long id, User admin) {
        requireAdmin(admin);
        EventDeletionRequest request = load(id);
        if (request.getStatus() != DeletionRequestStatus.PENDING) {
            throw new IllegalStateException("This request has already been processed.");
        }
        request.setStatus(DeletionRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        Event event = request.getEvent();
        event.setStatus(EventStatus.CANCELLED);
        videoStorageService.delete(event.getVideoPath());
        event.setVideoPath(null);
        event.setVideoFileName(null);
        event.setVideoContentType(null);
        return requestRepository.save(request);
    }

    @Transactional
    public EventDeletionRequest reject(Long id, User admin) {
        requireAdmin(admin);
        EventDeletionRequest request = load(id);
        if (request.getStatus() != DeletionRequestStatus.PENDING) {
            throw new IllegalStateException("This request has already been processed.");
        }
        request.setStatus(DeletionRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    private EventDeletionRequest load(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("The request was not found."));
    }

    private void requireAdmin(User admin) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new SecurityException("You are not allowed to perform this operation.");
        }
    }
}
