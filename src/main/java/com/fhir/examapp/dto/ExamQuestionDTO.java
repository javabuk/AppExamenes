package com.fhir.examapp.dto;

import java.util.List;

public class ExamQuestionDTO {
    private Long id;
    private Long topicId;
    private String topicCode;
    private String topicName;
    private String questionType;
    private Integer difficulty;
    private String stem;
    private String codeSnippet;
    private List<ExamOptionDTO> options;

    // Campos disponibles para Modo Tutor (Instant Feedback) o tras evaluación
    private String explanation;
    private String fhirSpecUrl;
    private String correctOptionLabel;

    public ExamQuestionDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public String getTopicCode() { return topicCode; }
    public void setTopicCode(String topicCode) { this.topicCode = topicCode; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public List<ExamOptionDTO> getOptions() { return options; }
    public void setOptions(List<ExamOptionDTO> options) { this.options = options; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getFhirSpecUrl() { return fhirSpecUrl; }
    public void setFhirSpecUrl(String fhirSpecUrl) { this.fhirSpecUrl = fhirSpecUrl; }

    public String getCorrectOptionLabel() { return correctOptionLabel; }
    public void setCorrectOptionLabel(String correctOptionLabel) { this.correctOptionLabel = correctOptionLabel; }
}
