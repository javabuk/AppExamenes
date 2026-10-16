package com.fhir.examapp.dto;

import java.util.List;

public class QuestionReviewDTO {
    private Long questionId;
    private String topicName;
    private String stem;
    private String codeSnippet;
    private String selectedOptions;
    private String correctOptionLabel;
    private Boolean correct;
    private Boolean flagged;
    private Integer timeSpentSeconds;
    private String explanation;
    private String fhirSpecUrl;
    private List<ExamOptionDTO> options;

    public QuestionReviewDTO() {}

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public String getSelectedOptions() { return selectedOptions; }
    public void setSelectedOptions(String selectedOptions) { this.selectedOptions = selectedOptions; }

    public String getCorrectOptionLabel() { return correctOptionLabel; }
    public void setCorrectOptionLabel(String correctOptionLabel) { this.correctOptionLabel = correctOptionLabel; }

    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }

    public Boolean getFlagged() { return flagged; }
    public void setFlagged(Boolean flagged) { this.flagged = flagged; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getFhirSpecUrl() { return fhirSpecUrl; }
    public void setFhirSpecUrl(String fhirSpecUrl) { this.fhirSpecUrl = fhirSpecUrl; }

    public List<ExamOptionDTO> getOptions() { return options; }
    public void setOptions(List<ExamOptionDTO> options) { this.options = options; }
}
