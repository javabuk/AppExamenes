package com.fhir.examapp.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "question_srs_state")
public class QuestionSrsState {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId = 1L;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id")
    private Certification certification;

    @Column(name = "consecutive_correct")
    private Integer consecutiveCorrect = 0;

    @Column(name = "easiness_factor")
    private Double easinessFactor = 2.5;

    @Column(name = "interval_days")
    private Integer intervalDays = 1;

    @Column(name = "next_review_date")
    private String nextReviewDate;

    @Column(name = "updated_at")
    private String updatedAt;

    public QuestionSrsState() {}

    public QuestionSrsState(Long questionId) {
        this(1L, questionId);
    }

    public QuestionSrsState(Long userId, Long questionId) {
        this.userId = userId != null ? userId : 1L;
        this.questionId = questionId;
        this.consecutiveCorrect = 0;
        this.easinessFactor = 2.5;
        this.intervalDays = 1;
        this.nextReviewDate = LocalDate.now().toString();
        this.updatedAt = LocalDateTime.now().format(FORMATTER);
    }

    public QuestionSrsState(Question question) {
        this(1L, question != null ? question.getId() : null);
    }

    public QuestionSrsState(Long userId, Question question) {
        this(userId, question != null ? question.getId() : null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public Integer getConsecutiveCorrect() { return consecutiveCorrect; }
    public void setConsecutiveCorrect(Integer consecutiveCorrect) { this.consecutiveCorrect = consecutiveCorrect; }

    public Double getEasinessFactor() { return easinessFactor; }
    public void setEasinessFactor(Double easinessFactor) { this.easinessFactor = easinessFactor; }

    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }

    public LocalDate getNextReviewDate() {
        if (nextReviewDate == null || nextReviewDate.trim().isEmpty()) return null;
        try {
            if (nextReviewDate.contains(" ")) {
                return LocalDate.parse(nextReviewDate.substring(0, 10));
            }
            return LocalDate.parse(nextReviewDate);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate != null ? nextReviewDate.toString() : null;
    }

    public String getNextReviewDateString() { return nextReviewDate; }
    public void setNextReviewDateString(String nextReviewDate) { this.nextReviewDate = nextReviewDate; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Certification getCertification() { return certification; }
    public void setCertification(Certification certification) { this.certification = certification; }
}
