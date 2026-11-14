package com.fhir.examapp.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class UserCertificationAssignmentRequest {

    @NotEmpty(message = "Debe especificar al menos una certificación")
    private List<Long> certificationIds;

    private String targetExamDate;

    public UserCertificationAssignmentRequest() {}

    public List<Long> getCertificationIds() { return certificationIds; }
    public void setCertificationIds(List<Long> certificationIds) { this.certificationIds = certificationIds; }

    public String getTargetExamDate() { return targetExamDate; }
    public void setTargetExamDate(String targetExamDate) { this.targetExamDate = targetExamDate; }
}
