package com.fhir.examapp.controller;

import com.fhir.examapp.domain.AuditLog;
import com.fhir.examapp.dto.ExamResultDTO;
import com.fhir.examapp.dto.admin.AdminAggregateResultsDTO;
import com.fhir.examapp.dto.admin.AdminUserDetailDTO;
import com.fhir.examapp.dto.admin.AdminUserKpiDTO;
import com.fhir.examapp.repository.AuditLogRepository;
import com.fhir.examapp.service.AdminResultsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResultsController {

    private final AdminResultsService adminResultsService;
    private final AuditLogRepository auditLogRepository;

    public AdminResultsController(AdminResultsService adminResultsService,
                                  AuditLogRepository auditLogRepository) {
        this.adminResultsService = adminResultsService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/results/users")
    public ResponseEntity<List<AdminUserKpiDTO>> getUsersKpis(@RequestParam(required = false) Long certificationId) {
        return ResponseEntity.ok(adminResultsService.getAllUsersKpis(certificationId));
    }

    @GetMapping("/results/users/{userId}")
    public ResponseEntity<?> getUserDetail(
            @PathVariable Long userId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Long certificationId) {
        try {
            AdminUserDetailDTO detail = adminResultsService.getUserDetail(userId, mode, fromDate, toDate, certificationId);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/results/users/{userId}/attempts/{attemptId}")
    public ResponseEntity<?> getUserAttemptReview(
            @PathVariable Long userId,
            @PathVariable Long attemptId) {
        try {
            ExamResultDTO review = adminResultsService.getUserAttemptReview(userId, attemptId);
            return ResponseEntity.ok(review);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/results/aggregate")
    public ResponseEntity<AdminAggregateResultsDTO> getAggregateResults(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long certificationId) {
        return ResponseEntity.ok(adminResultsService.getAggregateResults(mode, fromDate, toDate, userId, certificationId));
    }

    @GetMapping(value = "/results/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Long certificationId) {
        String csv = adminResultsService.exportResultsCsv(userId, mode, fromDate, toDate, certificationId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        String filename = userId != null
                ? "fhir_resultados_usuario_" + userId + ".csv"
                : "fhir_resultados_global.csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> auditPage = auditLogRepository.findAllByOrderByIdDesc(
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)))
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", auditPage.getContent());
        result.put("page", auditPage.getNumber());
        result.put("size", auditPage.getSize());
        result.put("totalElements", auditPage.getTotalElements());
        result.put("totalPages", auditPage.getTotalPages());
        return ResponseEntity.ok(result);
    }
}
