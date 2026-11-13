package com.fhir.examapp.dto.admin;

public class AdminFailedQuestionDTO {
    private Long questionId;
    private String stem;
    private String topicCode;
    private String topicName;
    private int totalAnswered;
    private int failedCount;
    private int correctCount;
    private double failureRatePercent;

    public AdminFailedQuestionDTO() {
    }

    public AdminFailedQuestionDTO(Long questionId, String stem, String topicCode, String topicName,
                                  int totalAnswered, int failedCount, int correctCount, double failureRatePercent) {
        this.questionId = questionId;
        this.stem = stem;
        this.topicCode = topicCode;
        this.topicName = topicName;
        this.totalAnswered = totalAnswered;
        this.failedCount = failedCount;
        this.correctCount = correctCount;
        this.failureRatePercent = failureRatePercent;
    }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getTopicCode() { return topicCode; }
    public void setTopicCode(String topicCode) { this.topicCode = topicCode; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public int getTotalAnswered() { return totalAnswered; }
    public void setTotalAnswered(int totalAnswered) { this.totalAnswered = totalAnswered; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public double getFailureRatePercent() { return failureRatePercent; }
    public void setFailureRatePercent(double failureRatePercent) { this.failureRatePercent = failureRatePercent; }
}
