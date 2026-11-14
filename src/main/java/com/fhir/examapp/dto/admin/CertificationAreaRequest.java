package com.fhir.examapp.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CertificationAreaRequest {

    @NotBlank(message = "El código del área es obligatorio")
    @Size(min = 2, max = 50, message = "El código debe tener entre 2 y 50 caracteres")
    private String code;

    @NotBlank(message = "El nombre del área es obligatorio")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    private String description;

    @Positive(message = "El peso porcentual debe ser positivo")
    private double weightPercent = 10.0;

    private int sortOrder = 1;

    private String studyStatus = "PENDING";

    private boolean active = true;

    public CertificationAreaRequest() {}

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
}
