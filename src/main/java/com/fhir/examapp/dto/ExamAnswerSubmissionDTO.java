package com.fhir.examapp.dto;

public class ExamAnswerSubmissionDTO {
    private Long questionId;
    private String selectedOptions; // 'A' o 'B' o 'A,C'
    private Integer timeSpentSeconds;
    private Boolean flagged = false;

    public ExamAnswerSubmissionDTO() {}

    public ExamAnswerSubmissionDTO(Long questionId, String selectedOptions, Integer timeSpentSeconds, Boolean flagged) {
        this.questionId = questionId;
        this.selectedOptions = selectedOptions;
        this.timeSpentSeconds = timeSpentSeconds;
        this.flagged = flagged;
    }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getSelectedOptions() { return selectedOptions; }
    public void setSelectedOptions(String selectedOptions) { this.selectedOptions = selectedOptions; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public Boolean getFlagged() { return flagged; }
    public void setFlagged(Boolean flagged) { this.flagged = flagged; }
}
