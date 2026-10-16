package com.fhir.examapp.dto;

import java.util.List;

public class FhirResourceValidationRequest {
    private String resourceJson;

    public FhirResourceValidationRequest() {}

    public FhirResourceValidationRequest(String resourceJson) {
        this.resourceJson = resourceJson;
    }

    public String getResourceJson() { return resourceJson; }
    public void setResourceJson(String resourceJson) { this.resourceJson = resourceJson; }
}
