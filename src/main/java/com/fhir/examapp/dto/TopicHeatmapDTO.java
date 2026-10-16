package com.fhir.examapp.dto;

public class TopicHeatmapDTO {
    private Long topicId;
    private String code;
    private String name;
    private Double weightPercent;
    private Long totalAnswered;
    private Long correctAnswered;
    private Double accuracyPercent;
    private String statusColor; // 'GREEN', 'AMBER', 'RED', 'GRAY'
    private Double averageTimeSeconds;

    public TopicHeatmapDTO() {}

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getWeightPercent() { return weightPercent; }
    public void setWeightPercent(Double weightPercent) { this.weightPercent = weightPercent; }

    public Long getTotalAnswered() { return totalAnswered; }
    public void setTotalAnswered(Long totalAnswered) { this.totalAnswered = totalAnswered; }

    public Long getCorrectAnswered() { return correctAnswered; }
    public void setCorrectAnswered(Long correctAnswered) { this.correctAnswered = correctAnswered; }

    public Double getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(Double accuracyPercent) { this.accuracyPercent = accuracyPercent; }

    public String getStatusColor() { return statusColor; }
    public void setStatusColor(String statusColor) { this.statusColor = statusColor; }

    public Double getAverageTimeSeconds() { return averageTimeSeconds; }
    public void setAverageTimeSeconds(Double averageTimeSeconds) { this.averageTimeSeconds = averageTimeSeconds; }
}
