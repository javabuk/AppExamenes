package com.fhir.examapp.dto;

import java.util.List;
import java.util.Map;

public class FhirSearchValidationResult {
    private Boolean valid;
    private String httpMethod;
    private String resourceType;
    private Map<String, List<String>> parsedParameters;
    private List<String> issues;
    private List<String> positiveFindings;
    private String canonicalForm;

    public FhirSearchValidationResult() {}

    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public Map<String, List<String>> getParsedParameters() { return parsedParameters; }
    public void setParsedParameters(Map<String, List<String>> parsedParameters) { this.parsedParameters = parsedParameters; }

    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }

    public List<String> getPositiveFindings() { return positiveFindings; }
    public void setPositiveFindings(List<String> positiveFindings) { this.positiveFindings = positiveFindings; }

    public String getCanonicalForm() { return canonicalForm; }
    public void setCanonicalForm(String canonicalForm) { this.canonicalForm = canonicalForm; }
}
