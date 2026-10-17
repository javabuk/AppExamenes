package com.fhir.examapp.dto;

public class MaterialQuestionPerformanceDTO {
    private Long questionId;
    private String stem;
    private int timesAnswered;
    private int timesCorrect;
    private double accuracyPercent;
    private String status; // 'DOMINADA', 'EN_PROGRESO', 'A_REFORZAR', 'SIN_INTENTOS'

    public MaterialQuestionPerformanceDTO() {}

    public MaterialQuestionPerformanceDTO(Long questionId, String stem, int timesAnswered, int timesCorrect,
                                          double accuracyPercent, String status) {
        this.questionId = questionId;
        this.stem = stem;
        this.timesAnswered = timesAnswered;
        this.timesCorrect = timesCorrect;
        this.accuracyPercent = accuracyPercent;
        this.status = status;
    }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public int getTimesAnswered() { return timesAnswered; }
    public void setTimesAnswered(int timesAnswered) { this.timesAnswered = timesAnswered; }

    public int getTimesCorrect() { return timesCorrect; }
    public void setTimesCorrect(int timesCorrect) { this.timesCorrect = timesCorrect; }

    public double getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(double accuracyPercent) { this.accuracyPercent = accuracyPercent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
