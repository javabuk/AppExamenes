package com.fhir.examapp.service;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.CertificationArea;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.dto.admin.*;
import com.fhir.examapp.repository.*;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class AdminCertificationService {

    private final CertificationRepository certificationRepository;
    private final CertificationAreaRepository certificationAreaRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AuditService auditService;

    public AdminCertificationService(CertificationRepository certificationRepository,
                                     CertificationAreaRepository certificationAreaRepository,
                                     QuestionRepository questionRepository,
                                     ExamAttemptRepository examAttemptRepository,
                                     UserCertificationRepository userCertificationRepository,
                                     SecurityContextHelper securityContextHelper,
                                     AuditService auditService) {
        this.certificationRepository = certificationRepository;
        this.certificationAreaRepository = certificationAreaRepository;
        this.questionRepository = questionRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CertificationDTO> getAllCertifications() {
        List<Certification> certs = certificationRepository.findAllByOrderBySortOrderAsc();
        return certs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificationDTO getCertificationById(Long id) {
        Certification cert = certificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificación no encontrada: " + id));
        return toDTO(cert);
    }

    public CertificationDTO createCertification(CertificationCreateRequest req, String ipAddress) {
        String code = req.getCode() != null ? req.getCode().trim().toUpperCase() : "";
        if (code.length() < 2) {
            throw new IllegalArgumentException("El código de la certificación debe tener al menos 2 caracteres.");
        }
        if (certificationRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ya existe una certificación con el código: " + code);
        }

        Certification cert = new Certification();
        cert.setCode(code);
        cert.setName(req.getName().trim());
        cert.setDescription(req.getDescription());
        cert.setStandardVersion(req.getStandardVersion() != null ? req.getStandardVersion().trim().toUpperCase() : "R4");
        cert.setExamQuestionCount(req.getExamQuestionCount() > 0 ? req.getExamQuestionCount() : 50);
        cert.setExamDurationMinutes(req.getExamDurationMinutes() > 0 ? req.getExamDurationMinutes() : 90);
        cert.setPassingScorePercent(req.getPassingScorePercent() > 0 ? req.getPassingScorePercent() : 70.0);
        cert.setSpecBaseUrl(req.getSpecBaseUrl());
        cert.setFeatureFlags(req.getFeatureFlags() != null ? req.getFeatureFlags() : "REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS");
        cert.setColor(req.getColor() != null ? req.getColor() : "#3b82f6");
        cert.setIcon(req.getIcon() != null ? req.getIcon() : "🧬");
        cert.setSortOrder(req.getSortOrder());
        cert.setActive(req.isActive());
        cert.setCreatedAt(User.formatNow());
        cert.setUpdatedAt(User.formatNow());

        Certification saved = certificationRepository.save(cert);

        if (req.getAreas() != null && !req.getAreas().isEmpty()) {
            for (CertificationAreaRequest ar : req.getAreas()) {
                CertificationArea area = new CertificationArea();
                area.setCertification(saved);
                area.setCode(ar.getCode().trim().toUpperCase());
                area.setName(ar.getName().trim());
                area.setDescription(ar.getDescription());
                area.setWeightPercent(ar.getWeightPercent());
                area.setSortOrder(ar.getSortOrder());
                area.setStudyStatus(ar.getStudyStatus() != null ? ar.getStudyStatus() : "PENDING");
                area.setActive(ar.isActive());
                certificationAreaRepository.save(area);
            }
        }

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_CREATED", "CERTIFICATION", String.valueOf(saved.getId()),
                "Certificación creada: " + saved.getCode() + " (" + saved.getName() + ")", ipAddress);

        return toDTO(saved);
    }

    public CertificationDTO updateCertification(Long id, CertificationUpdateRequest req, String ipAddress) {
        Certification cert = certificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificación no encontrada: " + id));

        if (req.getName() != null && !req.getName().isBlank()) {
            cert.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            cert.setDescription(req.getDescription());
        }
        if (req.getStandardVersion() != null && !req.getStandardVersion().isBlank()) {
            cert.setStandardVersion(req.getStandardVersion().trim().toUpperCase());
        }
        if (req.getExamQuestionCount() != null && req.getExamQuestionCount() > 0) {
            cert.setExamQuestionCount(req.getExamQuestionCount());
        }
        if (req.getExamDurationMinutes() != null && req.getExamDurationMinutes() > 0) {
            cert.setExamDurationMinutes(req.getExamDurationMinutes());
        }
        if (req.getPassingScorePercent() != null && req.getPassingScorePercent() > 0) {
            cert.setPassingScorePercent(req.getPassingScorePercent());
        }
        if (req.getSpecBaseUrl() != null) {
            cert.setSpecBaseUrl(req.getSpecBaseUrl().trim());
        }
        if (req.getFeatureFlags() != null) {
            cert.setFeatureFlags(req.getFeatureFlags().trim());
        }
        if (req.getColor() != null) {
            cert.setColor(req.getColor().trim());
        }
        if (req.getIcon() != null) {
            cert.setIcon(req.getIcon().trim());
        }
        if (req.getSortOrder() != null) {
            cert.setSortOrder(req.getSortOrder());
        }
        if (req.getActive() != null) {
            cert.setActive(req.getActive());
        }

        cert.setUpdatedAt(User.formatNow());
        Certification saved = certificationRepository.save(cert);

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_UPDATED", "CERTIFICATION", String.valueOf(saved.getId()),
                "Certificación actualizada: " + saved.getCode(), ipAddress);

        return toDTO(saved);
    }

    public CertificationDTO duplicateCertification(Long id, String ipAddress) {
        Certification source = certificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificación origen no encontrada: " + id));

        String baseCode = source.getCode();
        String newCode = baseCode + "_COPY";
        int counter = 1;
        while (certificationRepository.existsByCodeIgnoreCase(newCode)) {
            newCode = baseCode + "_COPY_" + counter;
            counter++;
        }

        Certification clone = new Certification();
        clone.setCode(newCode);
        clone.setName(source.getName() + " (Copia)");
        clone.setDescription(source.getDescription());
        clone.setStandardVersion(source.getStandardVersion());
        clone.setExamQuestionCount(source.getExamQuestionCount());
        clone.setExamDurationMinutes(source.getExamDurationMinutes());
        clone.setPassingScorePercent(source.getPassingScorePercent());
        clone.setSpecBaseUrl(source.getSpecBaseUrl());
        clone.setFeatureFlags(source.getFeatureFlags());
        clone.setColor(source.getColor());
        clone.setIcon(source.getIcon());
        clone.setSortOrder(source.getSortOrder() + 1);
        clone.setActive(false); // Created as draft/inactive
        clone.setCreatedAt(User.formatNow());
        clone.setUpdatedAt(User.formatNow());

        Certification saved = certificationRepository.save(clone);

        List<CertificationArea> sourceAreas = certificationAreaRepository.findByCertificationIdOrderBySortOrderAsc(source.getId());
        for (CertificationArea sa : sourceAreas) {
            CertificationArea ca = new CertificationArea();
            ca.setCertification(saved);
            ca.setCode(sa.getCode() + "_C");
            ca.setName(sa.getName());
            ca.setDescription(sa.getDescription());
            ca.setWeightPercent(sa.getWeightPercent());
            ca.setSortOrder(sa.getSortOrder());
            ca.setStudyStatus(sa.getStudyStatus());
            ca.setActive(sa.isActive());
            certificationAreaRepository.save(ca);
        }

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_DUPLICATED", "CERTIFICATION", String.valueOf(saved.getId()),
                "Certificación duplicada desde " + source.getCode() + " -> " + saved.getCode(), ipAddress);

        return toDTO(saved);
    }

    public void deleteCertification(Long id, String ipAddress) {
        Certification cert = certificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificación no encontrada: " + id));

        long questionsCount = questionRepository.countActiveVerifiedByCertificationId(id);
        if (questionsCount > 0) {
            throw new IllegalStateException("No se puede eliminar la certificación porque contiene " + questionsCount +
                    " preguntas asociadas. Desactívela en su lugar.");
        }

        List<com.fhir.examapp.domain.ExamAttempt> attempts = examAttemptRepository.findAllFinishedByCertificationIdOrderByFinishedAtDesc(id);
        if (attempts != null && !attempts.isEmpty()) {
            throw new IllegalStateException("No se puede eliminar la certificación porque contiene " + attempts.size() +
                    " intentos de examen históricos. Desactívela en su lugar.");
        }

        certificationRepository.delete(cert);

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_DELETED", "CERTIFICATION", String.valueOf(id),
                "Certificación eliminada: " + cert.getCode(), ipAddress);
    }

    // ======================== GESTIÓN DE ÁREAS (SYLLABUS) ========================

    @Transactional(readOnly = true)
    public List<CertificationAreaDTO> getAreas(Long certId) {
        Certification cert = certificationRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("Certificación no encontrada: " + certId));
        List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdOrderBySortOrderAsc(cert.getId());
        return areas.stream()
                .map(a -> {
                    long qCount = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(certId, a.getId()).size();
                    return CertificationAreaDTO.fromEntity(a, qCount);
                })
                .collect(Collectors.toList());
    }

    public CertificationAreaDTO addArea(Long certId, CertificationAreaRequest req, String ipAddress) {
        Certification cert = certificationRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("Certificación no encontrada: " + certId));

        String code = req.getCode() != null ? req.getCode().trim().toUpperCase() : "";
        if (certificationAreaRepository.existsByCertificationIdAndCodeIgnoreCase(certId, code)) {
            throw new IllegalArgumentException("Ya existe un área con el código '" + code + "' en esta certificación.");
        }

        CertificationArea area = new CertificationArea();
        area.setCertification(cert);
        area.setCode(code);
        area.setName(req.getName().trim());
        area.setDescription(req.getDescription());
        area.setWeightPercent(req.getWeightPercent());
        area.setSortOrder(req.getSortOrder());
        area.setStudyStatus(req.getStudyStatus() != null ? req.getStudyStatus() : "PENDING");
        area.setActive(req.isActive());

        CertificationArea saved = certificationAreaRepository.save(area);

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_AREA_CREATED", "CERTIFICATION_AREA", String.valueOf(saved.getId()),
                "Área creada para " + cert.getCode() + ": " + saved.getCode(), ipAddress);

        return CertificationAreaDTO.fromEntity(saved, 0L);
    }

    public CertificationAreaDTO updateArea(Long certId, Long areaId, CertificationAreaRequest req, String ipAddress) {
        CertificationArea area = certificationAreaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Área no encontrada: " + areaId));

        if (!area.getCertification().getId().equals(certId)) {
            throw new IllegalArgumentException("El área no pertenece a la certificación indicada.");
        }

        if (req.getCode() != null && !req.getCode().isBlank()) {
            String newCode = req.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(area.getCode()) && certificationAreaRepository.existsByCertificationIdAndCodeIgnoreCase(certId, newCode)) {
                throw new IllegalArgumentException("Ya existe otra área con el código '" + newCode + "' en esta certificación.");
            }
            area.setCode(newCode);
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            area.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            area.setDescription(req.getDescription());
        }
        if (req.getWeightPercent() > 0) {
            area.setWeightPercent(req.getWeightPercent());
        }
        area.setSortOrder(req.getSortOrder());
        if (req.getStudyStatus() != null) {
            area.setStudyStatus(req.getStudyStatus());
        }
        area.setActive(req.isActive());

        CertificationArea saved = certificationAreaRepository.save(area);
        long qCount = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(certId, saved.getId()).size();

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_AREA_UPDATED", "CERTIFICATION_AREA", String.valueOf(saved.getId()),
                "Área actualizada: " + saved.getCode(), ipAddress);

        return CertificationAreaDTO.fromEntity(saved, qCount);
    }

    public void deleteArea(Long certId, Long areaId, String ipAddress) {
        CertificationArea area = certificationAreaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Área no encontrada: " + areaId));

        if (!area.getCertification().getId().equals(certId)) {
            throw new IllegalArgumentException("El área no pertenece a la certificación indicada.");
        }

        long qCount = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(certId, areaId).size();
        if (qCount > 0) {
            throw new IllegalStateException("No se puede eliminar el área porque contiene " + qCount +
                    " preguntas asociadas. Reasigne o desactive las preguntas antes de eliminarla.");
        }

        certificationAreaRepository.delete(area);

        User admin = securityContextHelper.getCurrentUser();
        auditService.logUserAction(admin, "CERTIFICATION_AREA_DELETED", "CERTIFICATION_AREA", String.valueOf(areaId),
                "Área eliminada: " + area.getCode(), ipAddress);
    }

    private CertificationDTO toDTO(Certification cert) {
        long qCount = questionRepository.countActiveVerifiedByCertificationId(cert.getId());
        long uCount = userCertificationRepository.findAll().stream()
                .filter(uc -> uc.getCertification().getId().equals(cert.getId()) && uc.isActive())
                .count();

        List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdOrderBySortOrderAsc(cert.getId());
        List<CertificationAreaDTO> areaDTOs = areas.stream()
                .map(a -> {
                    long areaQCount = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(cert.getId(), a.getId()).size();
                    return CertificationAreaDTO.fromEntity(a, areaQCount);
                })
                .collect(Collectors.toList());

        return CertificationDTO.fromEntity(cert, qCount, uCount, areaDTOs);
    }
}
