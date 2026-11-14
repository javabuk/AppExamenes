package com.fhir.examapp.dto.admin;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CertificationUpdateRequest {

    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    private String description;

    private String standardVersion;

    @Positive(message = "El número de preguntas debe ser mayor que 0")
    private Integer examQuestionCount;

    @Positive(message = "La duración en minutos debe ser mayor que 0")
    private Integer examDurationMinutes;

    @Positive(message = "La nota de corte debe ser mayor que 0")
    private Double passingScorePercent;

    private String specBaseUrl;

    private String featureFlags;

    private String color;

    private String icon;

    private Integer sortOrder;

    private Boolean active;

    public CertificationUpdateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }

    public Integer getExamQuestionCount() { return examQuestionCount; }
    public void setExamQuestionCount(Integer examQuestionCount) { this.examQuestionCount = examQuestionCount; }

    public Integer getExamDurationMinutes() { return examDurationMinutes; }
    public void setExamDurationMinutes(Integer examDurationMinutes) { this.examDurationMinutes = examDurationMinutes; }

    public Double getPassingScorePercent() { return passingScorePercent; }
    public void setPassingScorePercent(Double passingScorePercent) { this.passingScorePercent = passingScorePercent; }

    public String getSpecBaseUrl() { return specBaseUrl; }
    public void setSpecBaseUrl(String specBaseUrl) { this.specBaseUrl = specBaseUrl; }

    public String getFeatureFlags() { return featureFlags; }
    public void setFeatureFlags(String featureFlags) { this.featureFlags = featureFlags; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
