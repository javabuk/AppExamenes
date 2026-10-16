package com.fhir.examapp.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExamSessionDTO {
    private Long attemptId;
    private String mode;
    private LocalDateTime startedAt;
    private Integer totalQuestions;
    private Integer timeLimitMinutes; // p. ej. 90 min para SIMULATION, null para TUTOR
    private List<ExamQuestionDTO> questions;

    public ExamSessionDTO() {}

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(Integer timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }

    public List<ExamQuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<ExamQuestionDTO> questions) { this.questions = questions; }
}
