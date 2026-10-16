package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findByUserAndEvent(User user, Event event);
    List<Registration> findByUserOrderByCreatedAtDesc(User user);
    List<Registration> findByEventAndStatus(Event event, RegistrationStatus status);
    long countByEventAndStatus(Event event, RegistrationStatus status);
    List<Registration> findByEventAndStatusOrderByCreatedAtAsc(Event event, RegistrationStatus status);
}
