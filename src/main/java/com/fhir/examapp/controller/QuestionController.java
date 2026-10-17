package com.fhir.examapp.controller;

import com.fhir.examapp.domain.Question;
import com.fhir.examapp.dto.QuestionCreateDTO;
import com.fhir.examapp.service.QuestionBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
@CrossOrigin(origins = "*")
public class QuestionController {

    private final QuestionBankService questionBankService;

    public QuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String search) {
        if (topicId != null) {
            return ResponseEntity.ok(questionBankService.getQuestionsByTopic(topicId));
        }
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(questionBankService.searchQuestions(search));
        }
        return ResponseEntity.ok(questionBankService.getAllQuestions());
    }

    @PostMapping
    public ResponseEntity<Question> createQuestion(@RequestBody QuestionCreateDTO dto) {
        return ResponseEntity.ok(questionBankService.createQuestion(dto));
    }

    @PostMapping("/{id}/variant")
    public ResponseEntity<Question> generateVariant(@PathVariable Long id) {
        return ResponseEntity.ok(questionBankService.generateMirrorVariant(id));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importQuestions(
            @RequestBody String jsonPayload,
            @RequestParam(required = false) Long defaultTopicId,
            @RequestParam(required = false) Long defaultMaterialId) {
        try {
            int imported = questionBankService.importQuestionsFromJson(jsonPayload, defaultTopicId, defaultMaterialId);
            return ResponseEntity.ok(Map.of("success", true, "importedCount", imported));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteQuestion(@PathVariable Long id) {
        questionBankService.deleteQuestion(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
