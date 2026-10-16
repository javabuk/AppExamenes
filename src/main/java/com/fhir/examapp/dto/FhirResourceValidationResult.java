package com.fhir.examapp.dto;

import java.util.List;

public class FhirResourceValidationResult {
    private Boolean valid;
    private String resourceType;
    private String resourceId;
    private List<String> issues;
    private String formattedJson;
    private String summary;

    public FhirResourceValidationResult() {}

    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }

    public String getFormattedJson() { return formattedJson; }
    public void setFormattedJson(String formattedJson) { this.formattedJson = formattedJson; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
