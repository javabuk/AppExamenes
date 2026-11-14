package com.fhir.examapp.controller;

import com.fhir.examapp.dto.FhirResourceValidationRequest;
import com.fhir.examapp.dto.FhirResourceValidationResult;
import com.fhir.examapp.dto.FhirSearchValidationRequest;
import com.fhir.examapp.dto.FhirSearchValidationResult;
import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.service.FhirValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fhir")
public class FhirToolsController {

    private final FhirValidationService fhirValidationService;
    private final CertificationContextHelper certHelper;

    public FhirToolsController(FhirValidationService fhirValidationService,
                               CertificationContextHelper certHelper) {
        this.fhirValidationService = fhirValidationService;
        this.certHelper = certHelper;
    }

    @PostMapping("/validate-search")
    public ResponseEntity<FhirSearchValidationResult> validateSearch(
            @RequestHeader(value = "X-Certification", required = false) String certHeader,
            @RequestParam(value = "certification", required = false) String certParam,
            @AuthenticationPrincipal User user,
            @RequestBody FhirSearchValidationRequest request) {

        Certification cert = certHelper.resolveActiveCertification(certHeader, certParam, user);
        if (cert != null && !cert.hasFeature("REST_SEARCH_BUILDER")) {
            throw new AccessDeniedException("La herramienta REST Search Builder no está habilitada para la certificación activa (" + cert.getName() + ").");
        }

        return ResponseEntity.ok(fhirValidationService.validateSearchQuery(
                request.getQueryUrl(),
                request.getExpectedResourceType()
        ));
    }

    @PostMapping("/validate-resource")
    public ResponseEntity<FhirResourceValidationResult> validateResource(
            @RequestHeader(value = "X-Certification", required = false) String certHeader,
            @RequestParam(value = "certification", required = false) String certParam,
            @AuthenticationPrincipal User user,
            @RequestBody FhirResourceValidationRequest request) {

        Certification cert = certHelper.resolveActiveCertification(certHeader, certParam, user);
        if (cert != null && !cert.hasFeature("RESOURCE_VALIDATOR")) {
            throw new AccessDeniedException("La herramienta Resource Validator no está habilitada para la certificación activa (" + cert.getName() + ").");
        }

        String version = (cert != null) ? cert.getVersion() : "R4";
        return ResponseEntity.ok(fhirValidationService.validateResource(request.getResourceJson(), version));
    }
}
