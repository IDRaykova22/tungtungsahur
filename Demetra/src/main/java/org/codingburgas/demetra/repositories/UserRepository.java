package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
    List<User> findByDeactivatedAtIsNull(Sort sort);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    Page<User> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end,
                                                                  Pageable pageable);

    /** Only the timestamps, for grouping into hours, days or months. */
    @Query("select u.createdAt from User u where u.createdAt >= :start and u.createdAt < :end")
    List<LocalDateTime> findCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select u.role, count(u) from User u where u.createdAt >= :start and u.createdAt < :end group by u.role")
    List<Object[]> countByRoleBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
