package com.fhir.examapp.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CertificationCreateRequest {

    @NotBlank(message = "El código de la certificación es obligatorio")
    @Size(min = 2, max = 50, message = "El código debe tener entre 2 y 50 caracteres")
    private String code;

    @NotBlank(message = "El nombre de la certificación es obligatorio")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    private String description;

    private String standardVersion = "R4";

    @Positive(message = "El número de preguntas debe ser mayor que 0")
    private int examQuestionCount = 50;

    @Positive(message = "La duración en minutos debe ser mayor que 0")
    private int examDurationMinutes = 90;

    @Positive(message = "La nota de corte debe ser mayor que 0")
    private double passingScorePercent = 70.0;

    private String specBaseUrl = "https://hl7.org/fhir/R4/";

    private String featureFlags = "REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS";

    private String color = "#3b82f6";

    private String icon = "🧬";

    private int sortOrder = 1;

    private boolean active = true;

    private List<CertificationAreaRequest> areas;

    public CertificationCreateRequest() {}

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

    public List<CertificationAreaRequest> getAreas() { return areas; }
    public void setAreas(List<CertificationAreaRequest> areas) { this.areas = areas; }
}
