package com.fhir.examapp.dto;

import java.util.List;
import java.util.Map;

public class FhirSearchValidationRequest {
    private String queryUrl; // ej: "GET [base]/Observation?status=final&code=http://loinc.org|8867-4&_include=Observation:subject"
    private String expectedResourceType; // ej: "Observation"

    public FhirSearchValidationRequest() {}

    public FhirSearchValidationRequest(String queryUrl, String expectedResourceType) {
        this.queryUrl = queryUrl;
        this.expectedResourceType = expectedResourceType;
    }

    public String getQueryUrl() { return queryUrl; }
    public void setQueryUrl(String queryUrl) { this.queryUrl = queryUrl; }

    public String getExpectedResourceType() { return expectedResourceType; }
    public void setExpectedResourceType(String expectedResourceType) { this.expectedResourceType = expectedResourceType; }
}
