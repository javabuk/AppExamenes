package com.fhir.examapp.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.LocalDate;

@Entity
@Table(name = "question_srs_state")
public class QuestionSrsState implements Persistable<Long> {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "consecutive_correct")
    private Integer consecutiveCorrect = 0;

    @Column(name = "easiness_factor")
    private Double easinessFactor = 2.5;

    @Column(name = "interval_days")
    private Integer intervalDays = 1;

    @Column(name = "next_review_date")
    private String nextReviewDate;

    @Transient
    private boolean isNew = false;

    public QuestionSrsState() {
        this.isNew = false;
    }

    public QuestionSrsState(Long questionId) {
        this.questionId = questionId;
        this.consecutiveCorrect = 0;
        this.easinessFactor = 2.5;
        this.intervalDays = 1;
        this.nextReviewDate = LocalDate.now().toString();
        this.isNew = true;
    }

    public QuestionSrsState(Question question) {
        this(question != null ? question.getId() : null);
    }

    @Override
    public Long getId() {
        return questionId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

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
}
