package org.codingburgas.demetra.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_questions")
@Getter
@Setter
@NoArgsConstructor
public class ProjectQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asked_by_id", nullable = false)
    private User askedBy;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 5000)
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by_id")
    private User answeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectQuestionStatus status = ProjectQuestionStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime answeredAt;
}
