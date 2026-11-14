package com.fhir.examapp.controller;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.CertificationArea;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.domain.UserCertification;
import com.fhir.examapp.dto.UserCertificationSummaryDTO;
import com.fhir.examapp.repository.CertificationAreaRepository;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.UserCertificationRepository;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationRepository certificationRepository;
    private final CertificationAreaRepository certificationAreaRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final SecurityContextHelper securityContextHelper;
    private final CertificationContextHelper certificationContextHelper;

    public CertificationController(CertificationRepository certificationRepository,
                                   CertificationAreaRepository certificationAreaRepository,
                                   UserCertificationRepository userCertificationRepository,
                                   SecurityContextHelper securityContextHelper,
                                   CertificationContextHelper certificationContextHelper) {
        this.certificationRepository = certificationRepository;
        this.certificationAreaRepository = certificationAreaRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.securityContextHelper = securityContextHelper;
        this.certificationContextHelper = certificationContextHelper;
    }

    @GetMapping("/my")
    public ResponseEntity<List<UserCertificationSummaryDTO>> getMyCertifications() {
        User currentUser = securityContextHelper.getCurrentUser();
        List<UserCertification> userCerts = userCertificationRepository.findByUserIdAndActiveTrue(currentUser.getId());
        List<UserCertificationSummaryDTO> results = userCerts.stream()
                .map(UserCertificationSummaryDTO::from)
                .collect(Collectors.toList());

        if (currentUser.getRole() == Role.ADMIN) {
            List<Certification> allActive = certificationRepository.findByActiveTrueOrderBySortOrderAsc();
            for (Certification c : allActive) {
                boolean exists = results.stream().anyMatch(r -> r.getCertificationId().equals(c.getId()));
                if (!exists) {
                    results.add(UserCertificationSummaryDTO.from(c));
                }
            }
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/active")
    public ResponseEntity<UserCertificationSummaryDTO> getActiveCertification() {
        Certification active = certificationContextHelper.resolveActiveCertification();
        return ResponseEntity.ok(UserCertificationSummaryDTO.from(active));
    }

    @GetMapping("/areas")
    public ResponseEntity<List<CertificationArea>> getActiveCertificationAreas(@RequestParam(required = false) Long certificationId) {
        Certification cert = certificationContextHelper.resolveActiveCertification(certificationId);
        List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdAndActiveTrueOrderBySortOrderAsc(cert.getId());
        return ResponseEntity.ok(areas);
    }

    @GetMapping("/{id}/areas")
    public ResponseEntity<List<CertificationArea>> getAreasByCertificationId(@PathVariable Long id) {
        certificationContextHelper.resolveActiveCertification(id); // validates access
        List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdAndActiveTrueOrderBySortOrderAsc(id);
        return ResponseEntity.ok(areas);
    }
}
