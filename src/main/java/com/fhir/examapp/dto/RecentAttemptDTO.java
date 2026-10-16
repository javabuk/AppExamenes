package com.fhir.examapp.dto;

import java.time.LocalDateTime;

public class RecentAttemptDTO {
    private Long id;
    private String mode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Double scorePercent;
    private Integer totalQuestions;
    private Integer correctQuestions;

    public RecentAttemptDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Double getScorePercent() { return scorePercent; }
    public void setScorePercent(Double scorePercent) { this.scorePercent = scorePercent; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getCorrectQuestions() { return correctQuestions; }
    public void setCorrectQuestions(Integer correctQuestions) { this.correctQuestions = correctQuestions; }
}
