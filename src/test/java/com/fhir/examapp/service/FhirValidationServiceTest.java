package com.fhir.examapp.service;

import com.fhir.examapp.dto.FhirResourceValidationResult;
import com.fhir.examapp.dto.FhirSearchValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FhirValidationServiceTest {

    private FhirValidationService fhirValidationService;

    @BeforeEach
    void setUp() {
        fhirValidationService = new FhirValidationService();
    }

    @Test
    void testValidateSearchQuery_ValidInclude() {
        String url = "GET [base]/Observation?code=http://loinc.org|15074-8&status=final&_include=Observation:subject";
        FhirSearchValidationResult result = fhirValidationService.validateSearchQuery(url, "Observation");

        assertTrue(result.getValid());
        assertEquals("Observation", result.getResourceType());
        assertEquals("GET", result.getHttpMethod());
        assertNotNull(result.getParsedParameters().get("_include"));
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void testValidateSearchQuery_InvalidInclude() {
        String url = "GET [base]/Observation?_include=invalidNoColon";
        FhirSearchValidationResult result = fhirValidationService.validateSearchQuery(url, "Observation");

        assertFalse(result.getValid());
        assertFalse(result.getIssues().isEmpty());
    }

    @Test
    void testValidateResource_ValidPatient() {
        String patientJson = """
            {
              "resourceType": "Patient",
              "id": "123",
              "gender": "male"
            }
            """;
        FhirResourceValidationResult result = fhirValidationService.validateResource(patientJson);

        assertTrue(result.getValid());
        assertEquals("Patient", result.getResourceType());
        assertEquals("123", result.getResourceId());
    }

    @Test
    void testValidateResource_MissingMandatoryStatusInObservation() {
        String observationWithoutStatus = """
            {
              "resourceType": "Observation",
              "id": "obs-1",
              "code": { "text": "Heart rate" }
            }
            """;
        FhirResourceValidationResult result = fhirValidationService.validateResource(observationWithoutStatus);

        assertFalse(result.getValid());
        assertTrue(result.getIssues().stream().anyMatch(i -> i.contains("status")));
    }
}
