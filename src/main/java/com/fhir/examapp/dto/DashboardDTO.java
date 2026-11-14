package com.fhir.examapp.dto;

import java.util.List;

public class DashboardDTO {
    private Double readinessScore;
    private String readinessStatus;
    private Long totalQuestionsCount;
    private Long masteredQuestionsCount;
    private Long pendingErrorsCount;
    private Long dueReviewsCount;
    private Double averageTimePerQuestionSeconds;
    private Long certificationId;
    private String certificationCode;
    private String certificationName;
    private Double passingScorePercent;
    private List<TopicHeatmapDTO> topicHeatmaps;
    private List<RecentAttemptDTO> recentAttempts;

    public DashboardDTO() {}

    public Double getReadinessScore() { return readinessScore; }
    public void setReadinessScore(Double readinessScore) { this.readinessScore = readinessScore; }

    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }

    public Long getTotalQuestionsCount() { return totalQuestionsCount; }
    public void setTotalQuestionsCount(Long totalQuestionsCount) { this.totalQuestionsCount = totalQuestionsCount; }

    public Long getMasteredQuestionsCount() { return masteredQuestionsCount; }
    public void setMasteredQuestionsCount(Long masteredQuestionsCount) { this.masteredQuestionsCount = masteredQuestionsCount; }

    public Long getPendingErrorsCount() { return pendingErrorsCount; }
    public void setPendingErrorsCount(Long pendingErrorsCount) { this.pendingErrorsCount = pendingErrorsCount; }

    public Long getDueReviewsCount() { return dueReviewsCount; }
    public void setDueReviewsCount(Long dueReviewsCount) { this.dueReviewsCount = dueReviewsCount; }

    public Double getAverageTimePerQuestionSeconds() { return averageTimePerQuestionSeconds; }
    public void setAverageTimePerQuestionSeconds(Double averageTimePerQuestionSeconds) { this.averageTimePerQuestionSeconds = averageTimePerQuestionSeconds; }

    public Long getCertificationId() { return certificationId; }
    public void setCertificationId(Long certificationId) { this.certificationId = certificationId; }

    public String getCertificationCode() { return certificationCode; }
    public void setCertificationCode(String certificationCode) { this.certificationCode = certificationCode; }

    public String getCertificationName() { return certificationName; }
    public void setCertificationName(String certificationName) { this.certificationName = certificationName; }

    public Double getPassingScorePercent() { return passingScorePercent; }
    public void setPassingScorePercent(Double passingScorePercent) { this.passingScorePercent = passingScorePercent; }

    public List<TopicHeatmapDTO> getTopicHeatmaps() { return topicHeatmaps; }
    public void setTopicHeatmaps(List<TopicHeatmapDTO> topicHeatmaps) { this.topicHeatmaps = topicHeatmaps; }

    public List<RecentAttemptDTO> getRecentAttempts() { return recentAttempts; }
    public void setRecentAttempts(List<RecentAttemptDTO> recentAttempts) { this.recentAttempts = recentAttempts; }
}
