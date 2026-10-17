package com.fhir.examapp.dto;

public class MaterialEvolutionPointDTO {
    private Long attemptId;
    private int attemptNumber;
    private String startedAt;
    private String finishedAt;
    private String formattedDate;
    private String mode;
    private double scorePercent;
    private int correctCount;
    private int totalCount;
    private int durationSeconds;
    private boolean passed;

    public MaterialEvolutionPointDTO() {}

    public MaterialEvolutionPointDTO(Long attemptId, int attemptNumber, String startedAt, String finishedAt,
                                     String formattedDate, String mode, double scorePercent,
                                     int correctCount, int totalCount, int durationSeconds, boolean passed) {
        this.attemptId = attemptId;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.formattedDate = formattedDate;
        this.mode = mode;
        this.scorePercent = scorePercent;
        this.correctCount = correctCount;
        this.totalCount = totalCount;
        this.durationSeconds = durationSeconds;
        this.passed = passed;
    }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public String getFormattedDate() { return formattedDate; }
    public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public double getScorePercent() { return scorePercent; }
    public void setScorePercent(double scorePercent) { this.scorePercent = scorePercent; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
}
