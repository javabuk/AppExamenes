package com.fhir.examapp.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_attempts")
public class ExamAttempt {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mode; // 'SIMULATION', 'ADAPTIVE', 'TUTOR', 'ERRORS'

    @Column(name = "started_at")
    private String startedAt;

    @Column(name = "finished_at")
    private String finishedAt;

    @Column(name = "score_percent")
    private Double scorePercent;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamAttemptAnswer> answers = new ArrayList<>();

    public ExamAttempt() {}

    public ExamAttempt(String mode) {
        this.mode = mode;
        this.startedAt = LocalDateTime.now().format(FORMATTER);
    }

    public void addAnswer(ExamAttemptAnswer answer) {
        answers.add(answer);
        answer.setAttempt(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getStartedAt() {
        if (startedAt == null || startedAt.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(startedAt.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt != null ? startedAt.format(FORMATTER) : null;
    }

    public LocalDateTime getFinishedAt() {
        if (finishedAt == null || finishedAt.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(finishedAt.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt != null ? finishedAt.format(FORMATTER) : null;
    }

    public Double getScorePercent() { return scorePercent; }
    public void setScorePercent(Double scorePercent) { this.scorePercent = scorePercent; }

    public List<ExamAttemptAnswer> getAnswers() { return answers; }
    public void setAnswers(List<ExamAttemptAnswer> answers) { this.answers = answers; }
}
