package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "exam_attempt_answers")
public class ExamAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    @JsonIgnore
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "selected_options", nullable = false)
    private String selectedOptions; // 'B' o 'A,C'

    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "is_flagged")
    private Boolean flagged = false;

    public ExamAttemptAnswer() {}

    public ExamAttemptAnswer(Question question, String selectedOptions, Boolean correct, Integer timeSpentSeconds, Boolean flagged) {
        this.question = question;
        this.selectedOptions = selectedOptions;
        this.correct = correct;
        this.timeSpentSeconds = timeSpentSeconds;
        this.flagged = flagged;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ExamAttempt getAttempt() { return attempt; }
    public void setAttempt(ExamAttempt attempt) { this.attempt = attempt; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getSelectedOptions() { return selectedOptions; }
    public void setSelectedOptions(String selectedOptions) { this.selectedOptions = selectedOptions; }

    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public Boolean getFlagged() { return flagged; }
    public void setFlagged(Boolean flagged) { this.flagged = flagged; }
}
