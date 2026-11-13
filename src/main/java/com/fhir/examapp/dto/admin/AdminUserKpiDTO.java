package com.fhir.examapp.dto.admin;

public class AdminUserKpiDTO {
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private Boolean active;
    private String lastLoginAt;
    private int totalExams;
    private int passedExams;
    private double passRatePercent;
    private double averageScore;
    private double readinessScore;
    private String readinessStatus;
    private String lastAttemptAt;
    private Double lastAttemptScore;
    private String lastAttemptMode;
    private long masteredQuestionsCount;
    private long dueReviewsCount;

    public AdminUserKpiDTO() {
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public int getTotalExams() { return totalExams; }
    public void setTotalExams(int totalExams) { this.totalExams = totalExams; }

    public int getPassedExams() { return passedExams; }
    public void setPassedExams(int passedExams) { this.passedExams = passedExams; }

    public double getPassRatePercent() { return passRatePercent; }
    public void setPassRatePercent(double passRatePercent) { this.passRatePercent = passRatePercent; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public double getReadinessScore() { return readinessScore; }
    public void setReadinessScore(double readinessScore) { this.readinessScore = readinessScore; }

    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }

    public String getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(String lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public Double getLastAttemptScore() { return lastAttemptScore; }
    public void setLastAttemptScore(Double lastAttemptScore) { this.lastAttemptScore = lastAttemptScore; }

    public String getLastAttemptMode() { return lastAttemptMode; }
    public void setLastAttemptMode(String lastAttemptMode) { this.lastAttemptMode = lastAttemptMode; }

    public long getMasteredQuestionsCount() { return masteredQuestionsCount; }
    public void setMasteredQuestionsCount(long masteredQuestionsCount) { this.masteredQuestionsCount = masteredQuestionsCount; }

    public long getDueReviewsCount() { return dueReviewsCount; }
    public void setDueReviewsCount(long dueReviewsCount) { this.dueReviewsCount = dueReviewsCount; }
}
