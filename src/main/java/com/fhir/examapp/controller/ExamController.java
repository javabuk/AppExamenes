package com.fhir.examapp.controller;

import com.fhir.examapp.domain.ExamAttempt;
import com.fhir.examapp.dto.ExamResultDTO;
import com.fhir.examapp.dto.ExamSessionDTO;
import com.fhir.examapp.dto.ExamStartRequest;
import com.fhir.examapp.dto.ExamSubmitRequest;
import com.fhir.examapp.service.ExamEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamEngineService examEngineService;

    public ExamController(ExamEngineService examEngineService) {
        this.examEngineService = examEngineService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startExam(@RequestBody ExamStartRequest request) {
        try {
            return ResponseEntity.ok(examEngineService.startExam(request));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<ExamResultDTO> submitExam(@RequestBody ExamSubmitRequest request) {
        return ResponseEntity.ok(examEngineService.submitExam(request));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ExamAttempt>> getRecentAttempts(@RequestParam(required = false) Long certificationId) {
        return ResponseEntity.ok(examEngineService.getRecentAttempts(certificationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamResultDTO> getAttemptReview(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(examEngineService.getAttemptReview(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
