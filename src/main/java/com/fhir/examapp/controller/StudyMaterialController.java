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

    @PostMapping("/markdown/file")
    public ResponseEntity<?> uploadMarkdownFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("topicId") Long topicId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sourceType", required = false) String sourceType) {
        try {
            StudyMaterial saved = studyMaterialService.saveMarkdownFile(file, topicId, title, sourceType);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error procesando archivo Markdown: " + e.getMessage()));
        }
    }

    @PostMapping("/markdown/text")
    public ResponseEntity<?> saveMarkdownText(@RequestBody Map<String, Object> payload) {
        try {
            Long topicId = Long.valueOf(payload.get("topicId").toString());
            String title = (String) payload.get("title");
            String sourceType = payload.get("sourceType") != null ? payload.get("sourceType").toString() : null;
            String content = (String) payload.get("content");
            if (content == null || content.trim().isEmpty()) {
                content = (String) payload.get("contentText");
            }
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El contenido Markdown no puede estar vacío."));
            }
            StudyMaterial saved = studyMaterialService.saveMarkdownText(topicId, title, content, sourceType);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error guardando material Markdown: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudyMaterial> getMaterial(@PathVariable Long id) {
        StudyMaterial m = studyMaterialService.getMaterialById(id);
        return m != null ? ResponseEntity.ok(m) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/questions")
    public ResponseEntity<?> getMaterialQuestions(@PathVariable Long id) {
        return ResponseEntity.ok(studyMaterialService.getLinkedQuestions(id));
    }

    @PostMapping("/{id}/questions/attach")
    public ResponseEntity<?> attachQuestions(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            Object rawIds = payload.get("questionIds");
            java.util.List<Long> qIds = new java.util.ArrayList<>();
            if (rawIds instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        qIds.add(Long.valueOf(item.toString()));
                    }
                }
            }
            int attached = studyMaterialService.attachQuestionsToMaterial(id, qIds);
            return ResponseEntity.ok(Map.of("success", true, "attachedCount", attached));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    public ResponseEntity<?> detachQuestion(@PathVariable Long id, @PathVariable Long questionId) {
        try {
            studyMaterialService.detachQuestionFromMaterial(id, questionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getMaterialComments(@PathVariable Long id) {
        return ResponseEntity.ok(studyMaterialService.getCardComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addMaterialComment(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            Integer cardIndex = payload.get("cardIndex") != null ? Integer.valueOf(payload.get("cardIndex").toString()) : 0;
            String cardHeading = payload.get("cardHeading") != null ? payload.get("cardHeading").toString() : null;
            String contentMarkdown = payload.get("contentMarkdown") != null ? payload.get("contentMarkdown").toString() : null;
            if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
                contentMarkdown = payload.get("content") != null ? payload.get("content").toString() : null;
            }
            return ResponseEntity.ok(studyMaterialService.addCardComment(id, cardIndex, cardHeading, contentMarkdown));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error guardando comentario: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/comments/{commentId}")
    public ResponseEntity<?> updateMaterialComment(@PathVariable Long id, @PathVariable Long commentId, @RequestBody Map<String, Object> payload) {
        try {
            String contentMarkdown = payload.get("contentMarkdown") != null ? payload.get("contentMarkdown").toString() : null;
            if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
                contentMarkdown = payload.get("content") != null ? payload.get("content").toString() : null;
            }
            return ResponseEntity.ok(studyMaterialService.updateCardComment(id, commentId, contentMarkdown));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error actualizando comentario: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<?> deleteMaterialComment(@PathVariable Long id, @PathVariable Long commentId) {
        try {
            studyMaterialService.deleteCardComment(id, commentId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/practice-info")
    public ResponseEntity<?> getPracticeInfo(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(studyMaterialService.getPracticeInfo(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getMaterialStats(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(studyMaterialService.getMaterialStats(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/generate-question")
    public ResponseEntity<?> generateQuestion(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(studyMaterialService.generateQuestionFromMaterial(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error generando pregunta: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> getMaterialFile(
            @PathVariable Long id,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) {
        StudyMaterial m = studyMaterialService.getMaterialById(id);
        if (m == null) {
            return ResponseEntity.notFound().build();
        }

        java.io.File file = studyMaterialService.getMaterialPhysicalFile(id);
        if (file != null && file.exists()) {
            String filename = file.getName();
            String contentType = "application/octet-stream";
            boolean isPdf = filename.toLowerCase().endsWith(".pdf");
            boolean isMd = filename.toLowerCase().endsWith(".md");

            if (isPdf) {
                contentType = "application/pdf";
            } else if (isMd) {
                contentType = "text/markdown; charset=UTF-8";
            }

            String disposition = (download || isMd)
                    ? "attachment; filename=\"" + filename + "\""
                    : "inline; filename=\"" + filename + "\"";

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(new org.springframework.core.io.FileSystemResource(file));
        } else if (m.getContentText() != null && !m.getContentText().isEmpty()) {
            // Generación al vuelo para descarga de materiales sin archivo físico
            boolean isMd = "MARKDOWN".equalsIgnoreCase(m.getSourceType())
                    || "STUDY_CARDS".equalsIgnoreCase(m.getSourceType())
                    || "MANUAL".equalsIgnoreCase(m.getSourceType());
            String ext = isMd ? ".md" : ".txt";
            String contentType = isMd ? "text/markdown; charset=UTF-8" : "text/plain; charset=UTF-8";
            String safeTitle = (m.getTitle() != null ? m.getTitle() : "documento")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!safeTitle.toLowerCase().endsWith(ext)) {
                safeTitle += ext;
            }
            byte[] bytes = m.getContentText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeTitle + "\"")
                    .body(new org.springframework.core.io.ByteArrayResource(bytes));
        }

        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMaterial(@PathVariable Long id) {
        studyMaterialService.deleteMaterial(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
