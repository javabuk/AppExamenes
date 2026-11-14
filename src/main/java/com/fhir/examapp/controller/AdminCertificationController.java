package com.fhir.examapp.controller;

import com.fhir.examapp.dto.admin.*;
import com.fhir.examapp.service.AdminCertificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/certifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCertificationController {

    private final AdminCertificationService certificationService;

    public AdminCertificationController(AdminCertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @GetMapping
    public ResponseEntity<List<CertificationDTO>> getAllCertifications() {
        return ResponseEntity.ok(certificationService.getAllCertifications());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificationDTO> getCertificationById(@PathVariable Long id) {
        return ResponseEntity.ok(certificationService.getCertificationById(id));
    }

    @PostMapping
    public ResponseEntity<?> createCertification(@Valid @RequestBody CertificationCreateRequest request,
                                                HttpServletRequest httpRequest) {
        try {
            CertificationDTO created = certificationService.createCertification(request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCertification(@PathVariable Long id,
                                                @Valid @RequestBody CertificationUpdateRequest request,
                                                HttpServletRequest httpRequest) {
        try {
            CertificationDTO updated = certificationService.updateCertification(id, request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicateCertification(@PathVariable Long id,
                                                   HttpServletRequest httpRequest) {
        try {
            CertificationDTO duplicated = certificationService.duplicateCertification(id, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(duplicated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCertification(@PathVariable Long id,
                                                HttpServletRequest httpRequest) {
        try {
            certificationService.deleteCertification(id, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("success", true, "message", "Certificación eliminada correctamente"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ====================== ÁREAS (SYLLABUS) ======================

    @GetMapping("/{id}/areas")
    public ResponseEntity<List<CertificationAreaDTO>> getAreas(@PathVariable Long id) {
        return ResponseEntity.ok(certificationService.getAreas(id));
    }

    @PostMapping("/{id}/areas")
    public ResponseEntity<?> addArea(@PathVariable Long id,
                                     @Valid @RequestBody CertificationAreaRequest request,
                                     HttpServletRequest httpRequest) {
        try {
            CertificationAreaDTO created = certificationService.addArea(id, request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/areas/{areaId}")
    public ResponseEntity<?> updateArea(@PathVariable Long id,
                                       @PathVariable Long areaId,
                                       @Valid @RequestBody CertificationAreaRequest request,
                                       HttpServletRequest httpRequest) {
        try {
            CertificationAreaDTO updated = certificationService.updateArea(id, areaId, request, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/areas/{areaId}")
    public ResponseEntity<?> deleteArea(@PathVariable Long id,
                                       @PathVariable Long areaId,
                                       HttpServletRequest httpRequest) {
        try {
            certificationService.deleteArea(id, areaId, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("success", true, "message", "Área eliminada correctamente"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
