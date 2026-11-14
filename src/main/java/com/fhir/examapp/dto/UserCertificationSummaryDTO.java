package com.fhir.examapp.dto;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.UserCertification;

public class UserCertificationSummaryDTO {
    private Long id;
    private Long certificationId;
    private String code;
    private String name;
    private String description;
    private String standardVersion;
    private String featureFlags;
    private String color;
    private String icon;
    private int examQuestionCount;
    private int examDurationMinutes;
    private double passingScorePercent;
    private String specBaseUrl;
    private String targetExamDate;
    private boolean active;

    public UserCertificationSummaryDTO() {}

    public static UserCertificationSummaryDTO from(UserCertification uc) {
        if (uc == null || uc.getCertification() == null) return null;
        Certification cert = uc.getCertification();
        UserCertificationSummaryDTO dto = from(cert);
        dto.setId(cert.getId());
        dto.setCertificationId(cert.getId());
        dto.setTargetExamDate(uc.getTargetExamDate());
        dto.setActive(uc.isActive());
        return dto;
    }

    public static UserCertificationSummaryDTO from(Certification cert) {
        if (cert == null) return null;
        UserCertificationSummaryDTO dto = new UserCertificationSummaryDTO();
        dto.setId(cert.getId());
        dto.setCertificationId(cert.getId());
        dto.setCode(cert.getCode());
        dto.setName(cert.getName());
        dto.setDescription(cert.getDescription());
        dto.setStandardVersion(cert.getStandardVersion());
        dto.setFeatureFlags(cert.getFeatureFlags());
        dto.setColor(cert.getColor());
        dto.setIcon(cert.getIcon());
        dto.setExamQuestionCount(cert.getExamQuestionCount());
        dto.setExamDurationMinutes(cert.getExamDurationMinutes());
        dto.setPassingScorePercent(cert.getPassingScorePercent());
        dto.setSpecBaseUrl(cert.getSpecBaseUrl());
        dto.setActive(cert.isActive());
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

    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }

    public String getFeatureFlags() { return featureFlags; }
    public void setFeatureFlags(String featureFlags) { this.featureFlags = featureFlags; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getExamQuestionCount() { return examQuestionCount; }
    public void setExamQuestionCount(int examQuestionCount) { this.examQuestionCount = examQuestionCount; }

    public int getExamDurationMinutes() { return examDurationMinutes; }
    public void setExamDurationMinutes(int examDurationMinutes) { this.examDurationMinutes = examDurationMinutes; }

    public double getPassingScorePercent() { return passingScorePercent; }
    public void setPassingScorePercent(double passingScorePercent) { this.passingScorePercent = passingScorePercent; }

    public String getSpecBaseUrl() { return specBaseUrl; }
    public void setSpecBaseUrl(String specBaseUrl) { this.specBaseUrl = specBaseUrl; }

    public String getTargetExamDate() { return targetExamDate; }
    public void setTargetExamDate(String targetExamDate) { this.targetExamDate = targetExamDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
