package com.examportal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Exam exam;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private int totalQuestions;

    @Builder.Default
    private int attempted = 0;

    @Builder.Default
    private int correctAnswers = 0;

    @Builder.Default
    private int wrongAnswers = 0;

    @Builder.Default
    private int score = 0;

    @Builder.Default
    private int totalMarks = 0;

    @Builder.Default
    private double percentage = 0.0;

    @Builder.Default
    private boolean passed = false;

    /** IN_PROGRESS, COMPLETED, or AUTO_SUBMITTED */
    @Builder.Default
    private String status = "IN_PROGRESS";

    @Builder.Default
    private int warningCount = 0;

    /** Comma-separated question IDs defining the shuffled order */
    @Column(columnDefinition = "TEXT")
    private String questionOrder;

    /** Semicolon-separated questionId:shuffledABCD mappings (e.g. "5:CBDA;12:DCAB") */
    @Column(columnDefinition = "TEXT")
    private String optionMappings;

    @Builder.Default
    private boolean autoSubmitted = false;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudentAnswer> answers = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
