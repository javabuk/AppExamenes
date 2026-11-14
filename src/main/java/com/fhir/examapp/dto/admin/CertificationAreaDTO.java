package com.fhir.examapp.dto.admin;

import com.fhir.examapp.domain.CertificationArea;

public class CertificationAreaDTO {
    private Long id;
    private Long certificationId;
    private String code;
    private String name;
    private String description;
    private double weightPercent;
    private int sortOrder;
    private String studyStatus;
    private boolean active;
    private long questionCount;

    public CertificationAreaDTO() {}

    public static CertificationAreaDTO fromEntity(CertificationArea area, long questionCount) {
        if (area == null) return null;
        CertificationAreaDTO dto = new CertificationAreaDTO();
        dto.setId(area.getId());
        dto.setCertificationId(area.getCertification() != null ? area.getCertification().getId() : null);
        dto.setCode(area.getCode());
        dto.setName(area.getName());
        dto.setDescription(area.getDescription());
        dto.setWeightPercent(area.getWeightPercent());
        dto.setSortOrder(area.getSortOrder());
        dto.setStudyStatus(area.getStudyStatus());
        dto.setActive(area.isActive());
        dto.setQuestionCount(questionCount);
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCertificationId() { return certificationId; }
    public void setCertificationId(Long certificationId) { this.certificationId = certificationId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getWeightPercent() { return weightPercent; }
    public void setWeightPercent(double weightPercent) { this.weightPercent = weightPercent; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getStudyStatus() { return studyStatus; }
    public void setStudyStatus(String studyStatus) { this.studyStatus = studyStatus; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getQuestionCount() { return questionCount; }
    public void setQuestionCount(long questionCount) { this.questionCount = questionCount; }
}
