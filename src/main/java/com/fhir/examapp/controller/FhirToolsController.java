package com.fhir.examapp.controller;

import com.fhir.examapp.dto.FhirResourceValidationRequest;
import com.fhir.examapp.dto.FhirResourceValidationResult;
import com.fhir.examapp.dto.FhirSearchValidationRequest;
import com.fhir.examapp.dto.FhirSearchValidationResult;
import com.fhir.examapp.service.FhirValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fhir")
@CrossOrigin(origins = "*")
public class FhirToolsController {

    private final FhirValidationService fhirValidationService;

    public FhirToolsController(FhirValidationService fhirValidationService) {
        this.fhirValidationService = fhirValidationService;
    }

    @PostMapping("/validate-search")
    public ResponseEntity<FhirSearchValidationResult> validateSearch(@RequestBody FhirSearchValidationRequest request) {
        return ResponseEntity.ok(fhirValidationService.validateSearchQuery(
                request.getQueryUrl(),
                request.getExpectedResourceType()
        ));
    }

    @PostMapping("/validate-resource")
    public ResponseEntity<FhirResourceValidationResult> validateResource(@RequestBody FhirResourceValidationRequest request) {
        return ResponseEntity.ok(fhirValidationService.validateResource(request.getResourceJson()));
    }
}
