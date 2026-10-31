package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataQualityService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final EventDeletionRequestRepository deletionRequestRepository;
    private final ProjectQuestionRepository questionRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataQualityService(UserRepository userRepository, EventRepository eventRepository,
                              RegistrationRepository registrationRepository,
                              EventDeletionRequestRepository deletionRequestRepository,
                              ProjectQuestionRepository questionRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.deletionRequestRepository = deletionRequestRepository;
        this.questionRepository = questionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DataQualityReport inspect() {
        List<DataQualityFinding> findings = new ArrayList<>();
        userRepository.findAll().forEach(user -> {
            if (user.getEmail() == null || user.getEmail().isBlank()) finding(findings, "User", user.getId(), "Missing email", "Email is required.");
            else if (!user.getEmail().contains("@")) finding(findings, "User", user.getId(), "Invalid email", "Email does not contain @.");
            if (user.getName() == null || user.getName().isBlank()) finding(findings, "User", user.getId(), "Missing name", "Name is required.");
            if (user.getRole() == null) finding(findings, "User", user.getId(), "Missing role", "Every user must have a role.");
        });
        eventRepository.findAll().forEach(event -> {
            if (event.getTitle() == null || event.getTitle().isBlank()) finding(findings, "Event", event.getId(), "Missing title", "Title is required.");
            if (event.getOrganizer() == null) finding(findings, "Event", event.getId(), "Missing organizer", "Every event must have an organizer.");
            if (event.getCapacity() < 1) finding(findings, "Event", event.getId(), "Invalid capacity", "Capacity must be greater than zero.");
            if (event.getPrice() < 0) finding(findings, "Event", event.getId(), "Invalid price", "Price cannot be negative.");
            if (event.getStatus() == null) finding(findings, "Event", event.getId(), "Missing status", "Every event must have a status.");
        });
        registrationRepository.findAll().forEach(registration -> {
            if (registration.getUser() == null) finding(findings, "Registration", registration.getId(), "Missing user", "Registration has no linked user.");
            if (registration.getEvent() == null) finding(findings, "Registration", registration.getId(), "Missing event", "Registration has no linked event.");
            if (registration.getStatus() == null) finding(findings, "Registration", registration.getId(), "Missing status", "Every registration must have a status.");
        });
        deletionRequestRepository.findAll().forEach(request -> {
            if (request.getEvent() == null) finding(findings, "Deletion request", request.getId(), "Missing event", "Request has no linked event.");
            if (request.getRequestedBy() == null) finding(findings, "Deletion request", request.getId(), "Missing requester", "Request has no requester.");
            if (request.getReason() == null || request.getReason().isBlank()) finding(findings, "Deletion request", request.getId(), "Missing reason", "A reason is required.");
            if (request.getStatus() == null) finding(findings, "Deletion request", request.getId(), "Missing status", "Every request must have a status.");
        });
        questionRepository.findAll().forEach(question -> {
            if (question.getEvent() == null) finding(findings, "Event question", question.getId(), "Missing event", "Question has no linked event.");
            if (question.getAskedBy() == null) finding(findings, "Event question", question.getId(), "Missing author", "Question has no author.");
            if (question.getQuestion() == null || question.getQuestion().isBlank()) finding(findings, "Event question", question.getId(), "Missing question", "Question text is required.");
            if (question.getStatus() == null) finding(findings, "Event question", question.getId(), "Missing status", "Every question must have a status.");
            if (question.getStatus() == ProjectQuestionStatus.ANSWERED && (question.getAnswer() == null || question.getAnsweredBy() == null)) {
                finding(findings, "Event question", question.getId(), "Incomplete answer", "Answered questions need an answer and a responder.");
            }
        });
        return new DataQualityReport(findings, expiredSessions());
    }

    private List<ExpiredSessionRecord> expiredSessions() {
        long now = System.currentTimeMillis();
        return jdbcTemplate.query("SELECT SESSION_ID, PRINCIPAL_NAME, CREATION_TIME, LAST_ACCESS_TIME, EXPIRY_TIME FROM SPRING_SESSION WHERE EXPIRY_TIME < ? ORDER BY EXPIRY_TIME DESC",
                (rs, rowNum) -> new ExpiredSessionRecord(rs.getString("SESSION_ID"), rs.getString("PRINCIPAL_NAME"),
                        Instant.ofEpochMilli(rs.getLong("CREATION_TIME")), Instant.ofEpochMilli(rs.getLong("LAST_ACCESS_TIME")),
                        Instant.ofEpochMilli(rs.getLong("EXPIRY_TIME"))), now);
    }

    private void finding(List<DataQualityFinding> findings, String type, Long id, String issue, String details) {
        findings.add(new DataQualityFinding(type, String.valueOf(id), issue, details));
    }
}
