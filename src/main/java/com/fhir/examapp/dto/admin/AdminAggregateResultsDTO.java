package com.fhir.examapp.dto.admin;

import com.fhir.examapp.dto.TopicHeatmapDTO;

import java.util.ArrayList;
import java.util.List;

public class AdminAggregateResultsDTO {
    private long totalUsers;
    private long activeUsers;
    private int totalCompletedExams;
    private int totalPassedExams;
    private double globalPassRatePercent;
    private double globalAverageScore;
    private long totalQuestionsInBank;
    private long totalMaterialsCount;
    private List<TopicHeatmapDTO> topicAverages = new ArrayList<>();
    private List<AdminFailedQuestionDTO> mostFailedQuestions = new ArrayList<>();
    private List<AdminUserKpiDTO> userRanking = new ArrayList<>();

    public AdminAggregateResultsDTO() {
    }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }

    public int getTotalCompletedExams() { return totalCompletedExams; }
    public void setTotalCompletedExams(int totalCompletedExams) { this.totalCompletedExams = totalCompletedExams; }

    public int getTotalPassedExams() { return totalPassedExams; }
    public void setTotalPassedExams(int totalPassedExams) { this.totalPassedExams = totalPassedExams; }

    public double getGlobalPassRatePercent() { return globalPassRatePercent; }
    public void setGlobalPassRatePercent(double globalPassRatePercent) { this.globalPassRatePercent = globalPassRatePercent; }

    public double getGlobalAverageScore() { return globalAverageScore; }
    public void setGlobalAverageScore(double globalAverageScore) { this.globalAverageScore = globalAverageScore; }

    public long getTotalQuestionsInBank() { return totalQuestionsInBank; }
    public void setTotalQuestionsInBank(long totalQuestionsInBank) { this.totalQuestionsInBank = totalQuestionsInBank; }

    public long getTotalMaterialsCount() { return totalMaterialsCount; }
    public void setTotalMaterialsCount(long totalMaterialsCount) { this.totalMaterialsCount = totalMaterialsCount; }

    public List<TopicHeatmapDTO> getTopicAverages() { return topicAverages; }
    public void setTopicAverages(List<TopicHeatmapDTO> topicAverages) { this.topicAverages = topicAverages; }

    public List<AdminFailedQuestionDTO> getMostFailedQuestions() { return mostFailedQuestions; }
    public void setMostFailedQuestions(List<AdminFailedQuestionDTO> mostFailedQuestions) { this.mostFailedQuestions = mostFailedQuestions; }

    public List<AdminUserKpiDTO> getUserRanking() { return userRanking; }
    public void setUserRanking(List<AdminUserKpiDTO> userRanking) { this.userRanking = userRanking; }
}
