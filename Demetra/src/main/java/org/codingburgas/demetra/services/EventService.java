package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;

    public EventService(EventRepository eventRepository, RegistrationRepository registrationRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
    }

    public List<Event> listPublishedEvents() {
        return eventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<Event> filterPublishedEvents(YearMonth month, int week) {
        if (week < 0 || week > 5 || (month == null && week != 0)) {
            throw new IllegalArgumentException("Choose a month and a week from 1 to 5, or all weeks.");
        }
        if (month == null) {
            return listPublishedEvents();
        }

        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        if (week > 0) {
            start = start.plusWeeks(week - 1);
            if (!start.isBefore(end)) {
                return List.of();
            }
            LocalDateTime weekEnd = start.plusWeeks(1);
            if (weekEnd.isBefore(end)) {
                end = weekEnd;
            }
        }

        return eventRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                EventStatus.PUBLISHED, start, end);
    }

    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }

    @Transactional
    public Event createEvent(Event event, User organizer) {
        event.setOrganizer(organizer);
        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }

    public long confirmedCount(Event event) {
        return registrationRepository.countByEventAndStatus(event, RegistrationStatus.CONFIRMED);
    }

    public Optional<Registration> findRegistration(User user, Event event) {
        return registrationRepository.findByUserAndEvent(user, event);
    }

    public List<Registration> myRegistrations(User user) {
        return registrationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public Registration register(User user, Event event) {
        Optional<Registration> existing = registrationRepository.findByUserAndEvent(user, event);
        if (existing.isPresent() && existing.get().getStatus() != RegistrationStatus.CANCELLED) {
            throw new IllegalStateException("You are already registered for this event.");
        }

        long confirmed = confirmedCount(event);
        RegistrationStatus status = confirmed < event.getCapacity()
                ? RegistrationStatus.CONFIRMED
                : RegistrationStatus.WAITLISTED;

        if (existing.isPresent()) {
            Registration registration = existing.get();
            registration.setStatus(status);
            return registrationRepository.save(registration);
        }

        return registrationRepository.save(new Registration(user, event, status));
    }

    @Transactional
    public void cancelRegistration(User user, Event event) {
        Registration registration = registrationRepository.findByUserAndEvent(user, event)
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .orElseThrow(() -> new IllegalStateException("You are not registered for this event."));

        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);

        if (wasConfirmed) {
            registrationRepository.findByEventAndStatusOrderByCreatedAtAsc(event, RegistrationStatus.WAITLISTED)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setStatus(RegistrationStatus.CONFIRMED);
                        registrationRepository.save(next);
                    });
        }
    }
}
