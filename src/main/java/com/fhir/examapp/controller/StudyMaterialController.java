package com.fhir.examapp.controller;

import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.service.StudyMaterialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
@CrossOrigin(origins = "*")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    public StudyMaterialController(StudyMaterialService studyMaterialService) {
        this.studyMaterialService = studyMaterialService;
    }

    @GetMapping
    public ResponseEntity<List<StudyMaterial>> getMaterials(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String search) {
        if (topicId != null) {
            return ResponseEntity.ok(studyMaterialService.getMaterialsByTopic(topicId));
        }
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(studyMaterialService.searchMaterials(search));
        }
        return ResponseEntity.ok(studyMaterialService.getAllMaterials());
    }

    @PostMapping("/upload")
    public ResponseEntity<StudyMaterial> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("topicId") Long topicId,
            @RequestParam(value = "title", required = false) String title) {
        try {
            StudyMaterial saved = studyMaterialService.extractAndSavePdf(file, topicId, title);
            return ResponseEntity.ok(saved);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/url")
    public ResponseEntity<?> importFromUrl(@RequestBody Map<String, Object> payload) {
        try {
            String url = (String) payload.get("url");
            Long topicId = Long.valueOf(payload.get("topicId").toString());
            String title = (String) payload.get("title");

            StudyMaterial saved = studyMaterialService.extractAndSaveFromUrl(url, topicId, title);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error descargando o procesando la URL: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudyMaterial> getMaterial(@PathVariable Long id) {
        StudyMaterial m = studyMaterialService.getMaterialById(id);
        return m != null ? ResponseEntity.ok(m) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMaterial(@PathVariable Long id) {
        studyMaterialService.deleteMaterial(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
