package com.fhir.examapp.dto;

import java.util.ArrayList;
import java.util.List;

public class MaterialStatsDTO {
    private Long materialId;
    private String materialTitle;
    private String sourceType;
    private String topicName;
    private Long topicId;
    private long linkedQuestionsCount;

    // Métricas de Prácticas
    private int totalAttempts;
    private int passedAttempts;
    private int failedAttempts;
    private double passRatePercent;
    private double averageScore;
    private Double bestScore;
    private Double latestScore;
    private int totalQuestionsAnswered;
    private int totalCorrectAnswers;
    private double overallAccuracyPercent;
    private int averageTimeSeconds;

    // Evolución temporal para gráficos
    private List<MaterialEvolutionPointDTO> evolution = new ArrayList<>();

    // Desglose de preguntas del documento
    private List<MaterialQuestionPerformanceDTO> questionPerformance = new ArrayList<>();

    public MaterialStatsDTO() {}

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public String getMaterialTitle() { return materialTitle; }
    public void setMaterialTitle(String materialTitle) { this.materialTitle = materialTitle; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public long getLinkedQuestionsCount() { return linkedQuestionsCount; }
    public void setLinkedQuestionsCount(long linkedQuestionsCount) { this.linkedQuestionsCount = linkedQuestionsCount; }

    public int getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

    public int getPassedAttempts() { return passedAttempts; }
    public void setPassedAttempts(int passedAttempts) { this.passedAttempts = passedAttempts; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    public double getPassRatePercent() { return passRatePercent; }
    public void setPassRatePercent(double passRatePercent) { this.passRatePercent = passRatePercent; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public Double getBestScore() { return bestScore; }
    public void setBestScore(Double bestScore) { this.bestScore = bestScore; }

    public Double getLatestScore() { return latestScore; }
    public void setLatestScore(Double latestScore) { this.latestScore = latestScore; }

    public int getTotalQuestionsAnswered() { return totalQuestionsAnswered; }
    public void setTotalQuestionsAnswered(int totalQuestionsAnswered) { this.totalQuestionsAnswered = totalQuestionsAnswered; }

    public int getTotalCorrectAnswers() { return totalCorrectAnswers; }
    public void setTotalCorrectAnswers(int totalCorrectAnswers) { this.totalCorrectAnswers = totalCorrectAnswers; }

    public double getOverallAccuracyPercent() { return overallAccuracyPercent; }
    public void setOverallAccuracyPercent(double overallAccuracyPercent) { this.overallAccuracyPercent = overallAccuracyPercent; }

    public int getAverageTimeSeconds() { return averageTimeSeconds; }
    public void setAverageTimeSeconds(int averageTimeSeconds) { this.averageTimeSeconds = averageTimeSeconds; }

    public List<MaterialEvolutionPointDTO> getEvolution() { return evolution; }
    public void setEvolution(List<MaterialEvolutionPointDTO> evolution) { this.evolution = evolution; }

    public List<MaterialQuestionPerformanceDTO> getQuestionPerformance() { return questionPerformance; }
    public void setQuestionPerformance(List<MaterialQuestionPerformanceDTO> questionPerformance) { this.questionPerformance = questionPerformance; }
}
