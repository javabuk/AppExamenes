package com.fhir.examapp.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExamResultDTO {
    private Long attemptId;
    private String mode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Double scorePercent;
    private Integer correctCount;
    private Integer totalCount;
    private Integer totalTimeSeconds;
    private Boolean passed; // >= 80% (umbral seguro HL7 FHIR)
    private List<QuestionReviewDTO> reviews;

    public ExamResultDTO() {}

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Double getScorePercent() { return scorePercent; }
    public void setScorePercent(Double scorePercent) { this.scorePercent = scorePercent; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getTotalTimeSeconds() { return totalTimeSeconds; }
    public void setTotalTimeSeconds(Integer totalTimeSeconds) { this.totalTimeSeconds = totalTimeSeconds; }

    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }

    public List<QuestionReviewDTO> getReviews() { return reviews; }
    public void setReviews(List<QuestionReviewDTO> reviews) { this.reviews = reviews; }
}
