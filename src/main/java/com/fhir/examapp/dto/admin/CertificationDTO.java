package com.fhir.examapp.dto.admin;

import com.fhir.examapp.domain.Certification;

import java.util.List;

public class CertificationDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String standardVersion;
    private int examQuestionCount;
    private int examDurationMinutes;
    private double passingScorePercent;
    private String specBaseUrl;
    private String featureFlags;
    private String color;
    private String icon;
    private int sortOrder;
    private boolean active;
    private String createdAt;
    private String updatedAt;
    private long totalQuestionsCount;
    private long assignedUsersCount;
    private List<CertificationAreaDTO> areas;

    public CertificationDTO() {}

    public static CertificationDTO fromEntity(Certification cert, long questionsCount, long usersCount, List<CertificationAreaDTO> areas) {
        if (cert == null) return null;
        CertificationDTO dto = new CertificationDTO();
        dto.setId(cert.getId());
        dto.setCode(cert.getCode());
        dto.setName(cert.getName());
        dto.setDescription(cert.getDescription());
        dto.setStandardVersion(cert.getStandardVersion());
        dto.setExamQuestionCount(cert.getExamQuestionCount());
        dto.setExamDurationMinutes(cert.getExamDurationMinutes());
        dto.setPassingScorePercent(cert.getPassingScorePercent());
        dto.setSpecBaseUrl(cert.getSpecBaseUrl());
        dto.setFeatureFlags(cert.getFeatureFlags());
        dto.setColor(cert.getColor());
        dto.setIcon(cert.getIcon());
        dto.setSortOrder(cert.getSortOrder());
        dto.setActive(cert.isActive());
        dto.setCreatedAt(cert.getCreatedAt());
        dto.setUpdatedAt(cert.getUpdatedAt());
        dto.setTotalQuestionsCount(questionsCount);
        dto.setAssignedUsersCount(usersCount);
        dto.setAreas(areas);
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }

    public int getExamQuestionCount() { return examQuestionCount; }
    public void setExamQuestionCount(int examQuestionCount) { this.examQuestionCount = examQuestionCount; }

    public int getExamDurationMinutes() { return examDurationMinutes; }
    public void setExamDurationMinutes(int examDurationMinutes) { this.examDurationMinutes = examDurationMinutes; }

    public double getPassingScorePercent() { return passingScorePercent; }
    public void setPassingScorePercent(double passingScorePercent) { this.passingScorePercent = passingScorePercent; }

    public String getSpecBaseUrl() { return specBaseUrl; }
    public void setSpecBaseUrl(String specBaseUrl) { this.specBaseUrl = specBaseUrl; }

    public String getFeatureFlags() { return featureFlags; }
    public void setFeatureFlags(String featureFlags) { this.featureFlags = featureFlags; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public long getTotalQuestionsCount() { return totalQuestionsCount; }
    public void setTotalQuestionsCount(long totalQuestionsCount) { this.totalQuestionsCount = totalQuestionsCount; }

    public long getAssignedUsersCount() { return assignedUsersCount; }
    public void setAssignedUsersCount(long assignedUsersCount) { this.assignedUsersCount = assignedUsersCount; }

    public List<CertificationAreaDTO> getAreas() { return areas; }
    public void setAreas(List<CertificationAreaDTO> areas) { this.areas = areas; }
}
