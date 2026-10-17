package com.fhir.examapp.dto;

import java.util.List;

public class QuestionCreateDTO {
    private Long topicId;
    private Long materialId; // Opcional, para asociar la pregunta a un apunte concreto
    private String stem;
    private String codeSnippet;
    private String explanation;
    private String fhirSpecUrl;
    private Integer difficulty = 2;
    private String questionType = "SINGLE_CHOICE";
    private String source = "MANUAL";
    private Long parentQuestionId;
    private List<OptionCreateDTO> options;

    public QuestionCreateDTO() {}

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getFhirSpecUrl() { return fhirSpecUrl; }
    public void setFhirSpecUrl(String fhirSpecUrl) { this.fhirSpecUrl = fhirSpecUrl; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getParentQuestionId() { return parentQuestionId; }
    public void setParentQuestionId(Long parentQuestionId) { this.parentQuestionId = parentQuestionId; }

    public List<OptionCreateDTO> getOptions() { return options; }
    public void setOptions(List<OptionCreateDTO> options) { this.options = options; }

    public static class OptionCreateDTO {
        private String optionLabel;
        private String optionText;
        private Boolean correct;
        private String distractorReason;

        public OptionCreateDTO() {}

        public OptionCreateDTO(String optionLabel, String optionText, Boolean correct, String distractorReason) {
            this.optionLabel = optionLabel;
            this.optionText = optionText;
            this.correct = correct;
            this.distractorReason = distractorReason;
        }

        public String getOptionLabel() { return optionLabel; }
        public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

        public String getOptionText() { return optionText; }
        public void setOptionText(String optionText) { this.optionText = optionText; }

        public Boolean getCorrect() { return correct; }
        public void setCorrect(Boolean correct) { this.correct = correct; }

        public String getDistractorReason() { return distractorReason; }
        public void setDistractorReason(String distractorReason) { this.distractorReason = distractorReason; }
    }
}
