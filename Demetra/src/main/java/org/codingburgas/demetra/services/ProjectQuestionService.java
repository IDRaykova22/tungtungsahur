package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.ProjectQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectQuestionService {
    private final ProjectQuestionRepository repository;

    public ProjectQuestionService(ProjectQuestionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProjectQuestion> forChat(Event event, User viewer) {
        if (canAnswer(event, viewer)) {
            return repository.findByEventOrderByCreatedAtAsc(event);
        }
        return repository.findByEventAndAskedByOrderByCreatedAtAsc(event, viewer);
    }

    @Transactional(readOnly = true)
    public List<ProjectQuestion> pending() {
        return repository.findByStatusOrderByCreatedAtAsc(ProjectQuestionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ProjectQuestion> inboxFor(User user) {
        if (user.getRole() == Role.MANAGER || user.getRole() == Role.ADMIN) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        if (user.getRole() == Role.ORGANIZER) {
            return repository.findByEventOrganizerOrderByCreatedAtDesc(user);
        }
        return repository.findByAskedByOrderByCreatedAtDesc(user);
    }

    @Transactional
    public ProjectQuestion ask(Event event, User asker, String text) {
        String question = text == null ? "" : text.trim();
        if (question.length() < 3 || question.length() > 2000) {
            throw new IllegalArgumentException("The question must be between 3 and 2000 characters.");
        }
        ProjectQuestion item = new ProjectQuestion();
        item.setEvent(event);
        item.setAskedBy(asker);
        item.setQuestion(question);
        return repository.save(item);
    }

    @Transactional
    public ProjectQuestion answer(Long questionId, User responder, String text) {
        ProjectQuestion item = repository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("The question was not found."));
        return answerItem(item, responder, text);
    }

    @Transactional
    public ProjectQuestion answerForEvent(Long questionId, Long eventId, User responder, String text) {
        ProjectQuestion item = repository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("The question was not found."));
        if (!item.getEvent().getId().equals(eventId)) {
            throw new SecurityException("The question does not belong to this event.");
        }
        return answerItem(item, responder, text);
    }

    private ProjectQuestion answerItem(ProjectQuestion item, User responder, String text) {
        if (!canAnswer(item.getEvent(), responder)) {
            throw new SecurityException("Only a manager or the event organizer can reply.");
        }
        String answer = text == null ? "" : text.trim();
        if (answer.length() < 2 || answer.length() > 5000) {
            throw new IllegalArgumentException("The reply must be between 2 and 5000 characters.");
        }
        item.setAnswer(answer);
        item.setAnsweredBy(responder);
        item.setAnsweredAt(LocalDateTime.now());
        item.setStatus(ProjectQuestionStatus.ANSWERED);
        return repository.save(item);
    }

    public boolean canAnswer(Event event, User user) {
        return user.getRole() == Role.MANAGER
                || user.getRole() == Role.ADMIN
                || (event.getOrganizer() != null && event.getOrganizer().getId().equals(user.getId()));
    }
}
