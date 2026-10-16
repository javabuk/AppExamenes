package com.fhir.examapp.dto;

import java.time.LocalDate;
import java.util.List;

public class FlashcardDTO {
    private Long questionId;
    private String topicName;
    private String stem;
    private String codeSnippet;
    private String correctOptionLabel;
    private String correctOptionText;
    private String explanation;
    private String fhirSpecUrl;
    private Integer consecutiveCorrect;
    private Double easinessFactor;
    private Integer intervalDays;
    private LocalDate nextReviewDate;
    private List<ExamOptionDTO> options;

    public FlashcardDTO() {}

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public String getCorrectOptionLabel() { return correctOptionLabel; }
    public void setCorrectOptionLabel(String correctOptionLabel) { this.correctOptionLabel = correctOptionLabel; }

    public String getCorrectOptionText() { return correctOptionText; }
    public void setCorrectOptionText(String correctOptionText) { this.correctOptionText = correctOptionText; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getFhirSpecUrl() { return fhirSpecUrl; }
    public void setFhirSpecUrl(String fhirSpecUrl) { this.fhirSpecUrl = fhirSpecUrl; }

    public Integer getConsecutiveCorrect() { return consecutiveCorrect; }
    public void setConsecutiveCorrect(Integer consecutiveCorrect) { this.consecutiveCorrect = consecutiveCorrect; }

    public Double getEasinessFactor() { return easinessFactor; }
    public void setEasinessFactor(Double easinessFactor) { this.easinessFactor = easinessFactor; }

    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }

    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }

    public List<ExamOptionDTO> getOptions() { return options; }
    public void setOptions(List<ExamOptionDTO> options) { this.options = options; }
}
