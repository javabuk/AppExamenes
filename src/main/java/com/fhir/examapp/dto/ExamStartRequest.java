package com.fhir.examapp.dto;

public class ExamStartRequest {
    private String mode; // 'SIMULATION', 'ADAPTIVE', 'TUTOR', 'ERRORS'
    private Integer questionCount;
    private Long topicId; // Opcional, para practicar un tema específico
    private Long materialId; // Opcional, para practicar un apunte o documento concreto

    public ExamStartRequest() {}

    public ExamStartRequest(String mode, Integer questionCount, Long topicId) {
        this.mode = mode;
        this.questionCount = questionCount;
        this.topicId = topicId;
    }

    public ExamStartRequest(String mode, Integer questionCount, Long topicId, Long materialId) {
        this.mode = mode;
        this.questionCount = questionCount;
        this.topicId = topicId;
        this.materialId = materialId;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
}
