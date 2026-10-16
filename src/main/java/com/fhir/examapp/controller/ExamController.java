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
@CrossOrigin(origins = "*")
public class ExamController {

    private final ExamEngineService examEngineService;

    public ExamController(ExamEngineService examEngineService) {
        this.examEngineService = examEngineService;
    }

    @PostMapping("/start")
    public ResponseEntity<ExamSessionDTO> startExam(@RequestBody ExamStartRequest request) {
        return ResponseEntity.ok(examEngineService.startExam(request));
    }

    @PostMapping("/submit")
    public ResponseEntity<ExamResultDTO> submitExam(@RequestBody ExamSubmitRequest request) {
        return ResponseEntity.ok(examEngineService.submitExam(request));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ExamAttempt>> getRecentAttempts() {
        return ResponseEntity.ok(examEngineService.getRecentAttempts());
    }
}
