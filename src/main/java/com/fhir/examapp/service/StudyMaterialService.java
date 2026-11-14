package com.fhir.examapp.service;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.CertificationArea;
import com.fhir.examapp.domain.ExamAttempt;
import com.fhir.examapp.domain.ExamAttemptAnswer;
import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.StudyCardComment;
import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.dto.MaterialEvolutionPointDTO;
import com.fhir.examapp.dto.MaterialQuestionPerformanceDTO;
import com.fhir.examapp.dto.MaterialStatsDTO;
import com.fhir.examapp.repository.CertificationAreaRepository;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.ExamAttemptAnswerRepository;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.repository.ExamAttemptRepository;
import com.fhir.examapp.repository.QuestionOptionRepository;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.StudyCardCommentRepository;
import com.fhir.examapp.repository.StudyMaterialRepository;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.security.access.AccessDeniedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final SyllabusTopicRepository syllabusTopicRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final SpacedRepetitionService srsService;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;
    private final StudyCardCommentRepository studyCardCommentRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AuditService auditService;
    private CertificationRepository certificationRepository;
    private CertificationAreaRepository certificationAreaRepository;
    private CertificationContextHelper certificationContextHelper;

    @Autowired
    public void setCertificationRepositories(@Autowired(required = false) CertificationRepository certificationRepository,
                                            @Autowired(required = false) CertificationAreaRepository certificationAreaRepository,
                                            @Autowired(required = false) CertificationContextHelper certificationContextHelper) {
        this.certificationRepository = certificationRepository;
        this.certificationAreaRepository = certificationAreaRepository;
        this.certificationContextHelper = certificationContextHelper;
    }

    private Long resolveCertId(Long explicitCertId) {
        if (explicitCertId != null) return explicitCertId;
        if (certificationContextHelper != null) {
            try {
                Certification cert = certificationContextHelper.resolveActiveCertification();
                if (cert != null) return cert.getId();
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository,
                                SyllabusTopicRepository syllabusTopicRepository,
                                QuestionRepository questionRepository,
                                QuestionOptionRepository questionOptionRepository,
                                SpacedRepetitionService srsService,
                                ExamAttemptRepository attemptRepository,
                                ExamAttemptAnswerRepository attemptAnswerRepository,
                                StudyCardCommentRepository studyCardCommentRepository) {
        this(studyMaterialRepository, syllabusTopicRepository, questionRepository,
                questionOptionRepository, srsService, attemptRepository,
                attemptAnswerRepository, studyCardCommentRepository, null, null);
    }

    @Autowired
    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository,
                                SyllabusTopicRepository syllabusTopicRepository,
                                QuestionRepository questionRepository,
                                QuestionOptionRepository questionOptionRepository,
                                SpacedRepetitionService srsService,
                                ExamAttemptRepository attemptRepository,
                                ExamAttemptAnswerRepository attemptAnswerRepository,
                                StudyCardCommentRepository studyCardCommentRepository,
                                SecurityContextHelper securityContextHelper,
                                AuditService auditService) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.syllabusTopicRepository = syllabusTopicRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.srsService = srsService;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.studyCardCommentRepository = studyCardCommentRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditService = auditService;
    }

    private User resolveCurrentUser() {
        if (securityContextHelper != null) {
            return securityContextHelper.getCurrentUser();
        }
        User fallback = new User("admin", "admin@fhir.local", "Admin", "", Role.ADMIN);
        fallback.setId(1L);
        return fallback;
    }

    private void checkMaterialOwnershipOrAdmin(StudyMaterial material) {
        User currentUser = resolveCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (material.getOwner() != null && material.getOwner().getId() != null
                && material.getOwner().getId().equals(currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("No tiene permiso para modificar o eliminar un material creado por otro usuario.");
    }

    private Certification resolveCertificationForTopicOrFallback(Long topicId) {
        if (certificationAreaRepository != null && topicId != null) {
            java.util.Optional<CertificationArea> areaOpt = certificationAreaRepository.findById(topicId);
            if (areaOpt.isPresent() && areaOpt.get().getCertification() != null) {
                return areaOpt.get().getCertification();
            }
        }
        if (certificationRepository != null) {
            return certificationRepository.findById(1L).orElse(null);
        }
        return null;
    }

    private CertificationArea resolveAreaForTopic(Long topicId) {
        if (certificationAreaRepository != null && topicId != null) {
            return certificationAreaRepository.findById(topicId).orElse(null);
        }
        return null;
    }

    /**
     * Procesa un PDF de apuntes o curso con Apache PDFBox y lo indexa asociado a un tema.
     */
    public StudyMaterial extractAndSavePdf(MultipartFile file, Long topicId, String customTitle) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        String title = (customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle
                : file.getOriginalFilename();

        String extractedText;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        }

        if (extractedText == null || extractedText.trim().isEmpty()) {
            extractedText = "(El PDF no contenía texto legible o es un documento escaneado/imagen)";
        }

        // Guardar archivo binario físico en ./data/materials/ para poder visualizar el original
        java.io.File storageDir = new java.io.File("./data/materials");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        String safeOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "document.pdf";
        String storedFilename = System.currentTimeMillis() + "_" + safeOriginal;
        java.io.File targetFile = new java.io.File(storageDir, storedFilename);
        java.nio.file.Files.write(targetFile.toPath(), file.getBytes());

        StudyMaterial material = new StudyMaterial(
                topic,
                "COURSE_PDF",
                title,
                extractedText,
                file.getOriginalFilename() + "#total"
        );
        material.setFilePath(targetFile.getPath());
        material.setOwner(resolveCurrentUser());
        material.setCreatedAt(User.formatNow());
        material.setDeleted(false);
        material.setCertification(resolveCertificationForTopicOrFallback(topic.getId()));
        material.setArea(resolveAreaForTopic(topic.getId()));

        return enrichMaterial(studyMaterialRepository.save(material));
    }

    /**
     * Descarga e indexa el contenido de una URL web o especificación FHIR R4 (HTML o PDF remoto).
     */
    public StudyMaterial extractAndSaveFromUrl(String urlString, Long topicId, String customTitle) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("La URL no puede estar vacía.");
        }
        urlString = urlString.trim();

        String title = (customTitle != null && !customTitle.trim().isEmpty()) ? customTitle.trim() : null;
        String contentText = "";
        String sourceType = "WEB_URL";

        if (urlString.toLowerCase().contains("hl7.org/fhir")) {
            sourceType = "OFFICIAL_FHIR_SPEC";
        }

        // 1. Si la URL apunta directamente a un archivo PDF remoto
        String downloadedPdfPath = null;
        if (urlString.toLowerCase().endsWith(".pdf")) {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                        .connectTimeout(java.time.Duration.ofSeconds(15))
                        .build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(urlString))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FHIR-ExamApp/1.0")
                        .GET()
                        .build();
                java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    try (PDDocument document = Loader.loadPDF(resp.body())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        contentText = stripper.getText(document);
                    }
                    sourceType = "COURSE_PDF";
                    if (title == null) {
                        String path = java.net.URI.create(urlString).getPath();
                        title = path.substring(path.lastIndexOf('/') + 1);
                    }
                    // Guardar copia física del PDF descargado
                    java.io.File storageDir = new java.io.File("./data/materials");
                    if (!storageDir.exists()) {
                        storageDir.mkdirs();
                    }
                    String safeOriginal = title != null ? title.replaceAll("[^a-zA-Z0-9._-]", "_") : "remote.pdf";
                    if (!safeOriginal.toLowerCase().endsWith(".pdf")) safeOriginal += ".pdf";
                    String storedFilename = System.currentTimeMillis() + "_" + safeOriginal;
                    java.io.File targetFile = new java.io.File(storageDir, storedFilename);
                    java.nio.file.Files.write(targetFile.toPath(), resp.body());
                    downloadedPdfPath = targetFile.getPath();
                }
            } catch (Exception ignored) {
                // Si falla como PDF directo, continuar con descarga web estándar
            }
        }

        // 2. Si es una página web o especificación HTML (ej: hl7.org/fhir/references.html)
        if (contentText.isEmpty()) {
            org.jsoup.Connection conn = org.jsoup.Jsoup.connect(urlString)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(20000)
                    .followRedirects(true)
                    .ignoreHttpErrors(false);

            org.jsoup.nodes.Document doc = conn.get();

            if (title == null) {
                title = doc.title();
                if (title == null || title.isBlank()) {
                    title = urlString;
                }
            }

            // Eliminar elementos no informativos (navegación, scripts, estilos, footer)
            doc.select("script, style, nav, footer, header, noscript, .nav, .footer, #header, #footer").remove();

            // Extraer el contenedor principal de la especificación si existe (#segment-content en HL7 FHIR)
            org.jsoup.nodes.Element contentElem = doc.selectFirst("#segment-content, main, article, .content, #content, body");
            if (contentElem != null) {
                contentText = contentElem.wholeText();
            } else {
                contentText = doc.body().wholeText();
            }

            // Normalizar espacios preservando saltos de párrafo
            contentText = contentText.replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n")
                    .trim();
        }

        if (contentText.isBlank()) {
            contentText = "(No se pudo extraer contenido textual legible de la URL proporcionada)";
        }

        StudyMaterial material = new StudyMaterial(
                topic,
                sourceType,
                title,
                contentText,
                urlString
        );
        if (downloadedPdfPath != null) {
            material.setFilePath(downloadedPdfPath);
        }
        material.setOwner(resolveCurrentUser());
        material.setCreatedAt(User.formatNow());
        material.setDeleted(false);
        material.setCertification(resolveCertificationForTopicOrFallback(topic.getId()));
        material.setArea(resolveAreaForTopic(topic.getId()));

        return enrichMaterial(studyMaterialRepository.save(material));
    }

    /**
     * Guarda e indexa un archivo de apuntes o fichas en formato Markdown (.md, .markdown, .txt).
     */
    public StudyMaterial saveMarkdownFile(MultipartFile file, Long topicId, String customTitle) throws IOException {
        return saveMarkdownFile(file, topicId, customTitle, null);
    }

    public StudyMaterial saveMarkdownFile(MultipartFile file, Long topicId, String customTitle, String requestedSourceType) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        String title = (customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle.trim()
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "Apuntes Markdown");

        String contentText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (contentText.isBlank()) {
            contentText = "(Archivo Markdown vacío)";
        }

        String resolvedSourceType = "MARKDOWN";
        if ("STUDY_CARDS".equalsIgnoreCase(requestedSourceType) || contentText.contains("### Ficha")) {
            resolvedSourceType = "STUDY_CARDS";
        }

        // Guardar archivo físico en ./data/materials/
        java.io.File storageDir = new java.io.File("./data/materials");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        String safeOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "material.md";
        if (!safeOriginal.toLowerCase().endsWith(".md")) {
            safeOriginal += ".md";
        }
        String storedFilename = System.currentTimeMillis() + "_" + safeOriginal;
        java.io.File targetFile = new java.io.File(storageDir, storedFilename);
        java.nio.file.Files.write(targetFile.toPath(), file.getBytes());

        StudyMaterial material = new StudyMaterial(
                topic,
                resolvedSourceType,
                title,
                contentText,
                safeOriginal
        );
        material.setFilePath(targetFile.getPath());
        material.setOwner(resolveCurrentUser());
        material.setCreatedAt(User.formatNow());
        material.setDeleted(false);
        material.setCertification(resolveCertificationForTopicOrFallback(topic.getId()));
        material.setArea(resolveAreaForTopic(topic.getId()));

        return enrichMaterial(studyMaterialRepository.save(material));
    }

    /**
     * Guarda e indexa contenido de apuntes o fichas escrito directamente en formato Markdown.
     */
    public StudyMaterial saveMarkdownText(Long topicId, String title, String markdownText) throws IOException {
        return saveMarkdownText(topicId, title, markdownText, null);
    }

    public StudyMaterial saveMarkdownText(Long topicId, String title, String markdownText, String requestedSourceType) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        if (title == null || title.trim().isEmpty()) {
            title = "STUDY_CARDS".equalsIgnoreCase(requestedSourceType) ? "Fichas de Aprendizaje FHIR" : "Apuntes Markdown";
        } else {
            title = title.trim();
        }

        if (markdownText == null || markdownText.trim().isEmpty()) {
            throw new IllegalArgumentException("El contenido Markdown no puede estar vacío.");
        }

        String resolvedSourceType = "MARKDOWN";
        if ("STUDY_CARDS".equalsIgnoreCase(requestedSourceType) || markdownText.contains("### Ficha")) {
            resolvedSourceType = "STUDY_CARDS";
        }

        // Guardar archivo físico .md en ./data/materials/
        java.io.File storageDir = new java.io.File("./data/materials");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        String safeTitle = title.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safeTitle.toLowerCase().endsWith(".md")) {
            safeTitle += ".md";
        }
        String storedFilename = System.currentTimeMillis() + "_" + safeTitle;
        java.io.File targetFile = new java.io.File(storageDir, storedFilename);
        java.nio.file.Files.writeString(targetFile.toPath(), markdownText, java.nio.charset.StandardCharsets.UTF_8);

        StudyMaterial material = new StudyMaterial(
                topic,
                resolvedSourceType,
                title,
                markdownText,
                safeTitle
        );
        material.setFilePath(targetFile.getPath());
        material.setOwner(resolveCurrentUser());
        material.setCreatedAt(User.formatNow());
        material.setDeleted(false);
        material.setCertification(resolveCertificationForTopicOrFallback(topic.getId()));
        material.setArea(resolveAreaForTopic(topic.getId()));

        return enrichMaterial(studyMaterialRepository.save(material));
    }

    public StudyMaterial createManualNote(Long topicId, String title, String content, String referenceUrl) {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        StudyMaterial material = new StudyMaterial(
                topic,
                "MANUAL",
                title,
                content,
                referenceUrl
        );
        material.setOwner(resolveCurrentUser());
        material.setCreatedAt(User.formatNow());
        material.setDeleted(false);
        material.setCertification(resolveCertificationForTopicOrFallback(topicId));
        material.setArea(resolveAreaForTopic(topicId));
        return enrichMaterial(studyMaterialRepository.save(material));
    }

    private StudyMaterial enrichMaterial(StudyMaterial m) {
        if (m != null && m.getId() != null) {
            Long currentUserId = resolveCurrentUser().getId();
            return enrichMaterial(m, currentUserId);
        }
        return m;
    }

    private StudyMaterial enrichMaterial(StudyMaterial m, Long currentUserId) {
        if (m != null && m.getId() != null) {
            m.setLinkedQuestionsCount(questionRepository.countByMaterialIdAndVerifiedTrue(m.getId()));
            m.setCommentsCount(studyCardCommentRepository.countByMaterialIdAndUser_Id(m.getId(), currentUserId));
        }
        return m;
    }

    private List<StudyMaterial> enrichMaterials(List<StudyMaterial> list) {
        if (list != null && !list.isEmpty()) {
            Long currentUserId = resolveCurrentUser().getId();
            for (StudyMaterial m : list) {
                enrichMaterial(m, currentUserId);
            }
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> getMaterialsByTopic(Long topicId) {
        Long certId = resolveCertId(null);
        if (certId != null) {
            return enrichMaterials(studyMaterialRepository.findByCertificationIdAndAreaId(certId, topicId));
        }
        return enrichMaterials(studyMaterialRepository.findByTopicId(topicId));
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> searchMaterials(String query) {
        return searchMaterials(query, null);
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> searchMaterials(String query, Long explicitCertId) {
        Long certId = resolveCertId(explicitCertId);
        if (query == null || query.trim().isEmpty()) {
            return getAllMaterials(certId);
        }
        if (certId != null) {
            return enrichMaterials(studyMaterialRepository.searchMaterialsByCertificationId(query.trim(), certId));
        }
        return enrichMaterials(studyMaterialRepository.searchMaterials(query.trim()));
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> getAllMaterials() {
        return getAllMaterials(null);
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> getAllMaterials(Long explicitCertId) {
        Long certId = resolveCertId(explicitCertId);
        if (certId != null) {
            return enrichMaterials(studyMaterialRepository.findAllActiveByCertificationId(certId));
        }
        return enrichMaterials(studyMaterialRepository.findAllActive());
    }

    @Transactional(readOnly = true)
    public StudyMaterial getMaterialById(Long id) {
        StudyMaterial m = studyMaterialRepository.findById(id).orElse(null);
        if (m == null || Boolean.TRUE.equals(m.getDeleted())) return null;
        return enrichMaterial(m);
    }

    public StudyMaterial updateMaterial(Long id, String title, String contentText, Long topicId) {
        StudyMaterial material = studyMaterialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + id));
        if (Boolean.TRUE.equals(material.getDeleted())) {
            throw new IllegalArgumentException("Material no encontrado: " + id);
        }

        checkMaterialOwnershipOrAdmin(material);

        if (title != null && !title.isBlank()) {
            material.setTitle(title.trim());
        }
        if (contentText != null && !contentText.isBlank()) {
            material.setContentText(contentText);
        }
        if (topicId != null) {
            syllabusTopicRepository.findById(topicId).ifPresent(material::setTopic);
        }
        material.setUpdatedAt(User.formatNow());
        StudyMaterial saved = studyMaterialRepository.save(material);

        if (auditService != null) {
            auditService.logUserAction(resolveCurrentUser(), "MATERIAL_UPDATED", "MATERIAL",
                    String.valueOf(saved.getId()), "Material editado: " + saved.getTitle(), "127.0.0.1");
        }
        return enrichMaterial(saved);
    }

    @Transactional(readOnly = true)
    public List<Question> getLinkedQuestions(Long materialId) {
        return questionRepository.findByMaterialIdAndVerifiedTrue(materialId);
    }

    public int attachQuestionsToMaterial(Long materialId, List<Long> questionIds) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + materialId));
        checkMaterialOwnershipOrAdmin(material);
        if (questionIds == null || questionIds.isEmpty()) return 0;

        int attached = 0;
        for (Long qId : questionIds) {
            if (qId == null) continue;
            Question q = questionRepository.findById(qId).orElse(null);
            if (q != null && !Boolean.TRUE.equals(q.getDeleted())) {
                q.setMaterial(material);
                questionRepository.save(q);
                attached++;
            }
        }
        return attached;
    }

    public void detachQuestionFromMaterial(Long materialId, Long questionId) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + materialId));
        checkMaterialOwnershipOrAdmin(material);

        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada: " + questionId));
        if (q.getMaterial() != null && materialId.equals(q.getMaterial().getId())) {
            q.setMaterial(null);
            questionRepository.save(q);
        }
    }

    // =================== COMENTARIOS Y NOTAS EN FICHAS DE ESTUDIO (PRIVADOS POR USUARIO) ===================

    @Transactional(readOnly = true)
    public List<StudyCardComment> getCardComments(Long materialId) {
        User currentUser = resolveCurrentUser();
        return studyCardCommentRepository.findByMaterialIdAndUser_IdOrderByCardIndexAscIdAsc(materialId, currentUser.getId());
    }

    public StudyCardComment addCardComment(Long materialId, Integer cardIndex, String cardHeading, String contentMarkdown) {
        User currentUser = resolveCurrentUser();
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Colección de fichas no encontrada: " + materialId));

        if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
            throw new IllegalArgumentException("El texto del comentario no puede estar vacío.");
        }

        int resolvedIndex = cardIndex != null && cardIndex >= 0 ? cardIndex : 0;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        StudyCardComment comment = new StudyCardComment(
                material,
                currentUser,
                resolvedIndex,
                cardHeading != null ? cardHeading.trim() : ("Ficha " + (resolvedIndex + 1)),
                contentMarkdown.trim(),
                now
        );
        return studyCardCommentRepository.save(comment);
    }

    public StudyCardComment updateCardComment(Long materialId, Long commentId, String contentMarkdown) {
        User currentUser = resolveCurrentUser();
        StudyCardComment comment = studyCardCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado: " + commentId));

        if (comment.getMaterial() == null || !materialId.equals(comment.getMaterial().getId())) {
            throw new IllegalArgumentException("El comentario no pertenece a esta colección de fichas.");
        }
        if (comment.getUser() != null && comment.getUser().getId() != null
                && !comment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No tiene permiso para editar la nota de otro usuario.");
        }
        if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
            throw new IllegalArgumentException("El texto del comentario no puede estar vacío.");
        }

        comment.setContentMarkdown(contentMarkdown.trim());
        comment.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return studyCardCommentRepository.save(comment);
    }

    public void deleteCardComment(Long materialId, Long commentId) {
        User currentUser = resolveCurrentUser();
        StudyCardComment comment = studyCardCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado: " + commentId));

        if (comment.getUser() != null && comment.getUser().getId() != null
                && !comment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No tiene permiso para eliminar la nota de otro usuario.");
        }

        if (comment.getMaterial() != null && materialId.equals(comment.getMaterial().getId())) {
            studyCardCommentRepository.delete(comment);
        }
    }

    public void ensureDefaultStudyCardsDeck() {
        try {
            List<StudyMaterial> existing = studyMaterialRepository.findAll();
            boolean hasStudyCards = existing.stream().anyMatch(m -> "STUDY_CARDS".equalsIgnoreCase(m.getSourceType()));
            if (hasStudyCards) return;

            List<SyllabusTopic> topics = syllabusTopicRepository.findAll();
            if (topics.isEmpty()) return;

            SyllabusTopic targetTopic = topics.get(0);
            for (SyllabusTopic t : topics) {
                String n = (t.getName() != null ? t.getName() : "").toLowerCase();
                String c = (t.getCode() != null ? t.getCode() : "").toLowerCase();
                if (n.contains("paradigma") || n.contains("rest") || n.contains("fundamento") || c.contains("rest") || c.contains("found")) {
                    targetTopic = t;
                    break;
                }
            }

            String defaultCardsMd = """
---

## 1. La idea general

### Ficha 1 · ¿De qué trata esta página?
**Respuesta:** Explica las **5 formas** que hay de mover datos con FHIR.
💡 Es como elegir transporte: tren, avión, coche… Cada uno sirve mejor según el viaje.

Las cinco formas:
1. **API REST**
2. **Mensajes** (*Messaging*)
3. **Documentos** (*Documents*)
4. **Servicios** (*Services / SOA*)
5. **Base de datos** (*Persistent store*)

### Ficha 2 · ¿Hay que elegir solo una?
**Respuesta:** **No.** Se pueden usar varias a la vez. La mejor depende de **cómo está montado tu sistema**.
⚠️ Ninguna "arregla" los defectos de otra. Mensajes no sustituye a REST ni REST a mensajes: son distintas.

### Ficha 3 · Resumen en una tabla
| Forma | Se parece a… | Se usa cuando… |
|---|---|---|
| REST | Ir a una ventanilla y pedir o dejar datos al momento | Quieres consultar o guardar datos concretos |
| Mensajes | Enviar una carta a otra organización | Las organizaciones casi no comparten procesos |
| Documentos | Enviar un informe cerrado y firmado | Quieres que lo lea una persona tal cual se emitió |
| Servicios | Una empresa con departamentos que se piden cosas | Tu arquitectura es por servicios |
| Base de datos | El archivador donde guardas todo | Quieres guardar los datos ya en formato FHIR |

---

## 2. API REST

### Ficha 4 · ¿Qué es?
**Respuesta:** Una forma de hablar entre un **cliente** y un **servidor**. El cliente puede **crear, leer, modificar, borrar, buscar y ejecutar operaciones**.
💡 Sirve para **enviar** datos (*push*) y para **pedirlos** (*pull*).

### Ficha 5 · ¿Qué más incluye?
**Respuesta:** Permite trabajo **asíncrono** (pides algo y la respuesta llega después) y consultas con **GraphQL**.

### Ficha 6 · 🧩 Caso de uso: la app del paciente
*Una app móvil muestra al paciente sus alergias.*
**Respuesta:** **REST.** La app pregunta "dame las alergias del paciente 123" y el servidor contesta al momento.
Se suele añadir **SMART App Launch** para controlar quién entra.

### Ficha 7 · 🧩 Caso de uso: dos sistemas que ya se conocen
*Dos hospitales de la misma red, con acuerdos firmados, comparten datos.*
**Respuesta:** **REST.** La página dice que va bien cuando ya hay **acuerdos de gobernanza** (reglas pactadas entre los participantes).

---

## 3. Mensajes (*Messaging*)

### Ficha 8 · ¿Qué es?
**Respuesta:** Enviar **mensajes** de un sistema a otro, que se encaminan hasta su destino.
💡 Como mandar una carta: tú envías, no necesitas ir a la ventanilla del otro.
Puede funcionar **encima de REST** o con otra tecnología de mensajería.

### Ficha 9 · 🎯 ¿Cuándo es buena idea?
**Respuesta:** Cuando se intercambia entre **organizaciones muy distintas** que **casi no comparten procesos**.
Frase de la página: puede ser *"más adecuado para el intercambio entre organizaciones dispares con bajo nivel de integración de procesos"*.

### Ficha 10 · 🧩 Caso de uso: laboratorio externo
*Un laboratorio de otra empresa avisa a varios hospitales de que hay un resultado. Cada hospital usa un sistema diferente.*
**Respuesta:** **Mensajes.** El laboratorio envía el mensaje y no necesita conocer cómo funciona cada hospital por dentro.

### Ficha 11 · 🧩 Para ti: HL7 v2 y HealthConnect
*Tú ya mueves ADT u ORU con un motor de integración.*
**Respuesta:** Los mensajes de FHIR son **parecidos en idea**: un evento sale de un sistema y llega a otros. Es la forma más cercana a lo que ya conoces de HL7 v2.

---
""".trim();

            StudyMaterial savedDeck = saveMarkdownText(
                    targetTopic.getId(),
                    "5 Formas de Mover Datos con FHIR (Paradigmas de Intercambio)",
                    defaultCardsMd,
                    "STUDY_CARDS"
            );

            // Crear 4 preguntas de autoevaluación directamente vinculadas a este mazo de fichas
            createDefaultCardQuestion(savedDeck, targetTopic,
                    "Según las fichas de paradigmas de intercambio en FHIR, ¿cuál es el enfoque más adecuado cuando una app móvil necesita consultar en tiempo real las alergias de un paciente concreto?",
                    "GET [base]/AllergyIntolerance?patient=Patient/123",
                    "Como detalla la Ficha 6, una app móvil que consulta datos puntuales al momento utiliza el paradigma API REST, habitualmente protegido mediante SMART App Launch.",
                    "A", "API REST (acompañado habitualmente de SMART App Launch).", "Correcto: la app realiza una consulta síncrona puntual sobre el servidor FHIR.",
                    "B", "Documentos cerrados y firmados exclusivamente.", "Los documentos están pensados para informes clínicos cerrados e inmutables, no para consultas granulares bajo demanda.",
                    "C", "Mensajería por lotes sin respuesta.", "La app requiere los datos del paciente al momento.",
                    "D", "Obligar a elegir un único paradigma en toda la organización.", "Como indica la Ficha 2, se pueden combinar varios paradigmas simultáneamente."
            );

            createDefaultCardQuestion(savedDeck, targetTopic,
                    "De acuerdo con la Ficha 9 y la Ficha 10, ¿cuándo resulta especialmente recomendable utilizar el paradigma de Mensajes (Messaging) en HL7 FHIR?",
                    "{\n  \"resourceType\": \"Bundle\",\n  \"type\": \"message\"\n}",
                    "La especificación indica que el intercambio por mensajes es más adecuado entre organizaciones dispares con bajo nivel de integración de procesos (ej. un laboratorio externo que notifica resultados a múltiples hospitales con sistemas distintos).",
                    "A", "Únicamente cuando ambos sistemas pertenecen al mismo departamento y comparten la misma base de datos.", "En ese escenario no hay disparidad organizativa.",
                    "B", "En el intercambio entre organizaciones dispares con bajo nivel de integración de procesos.", "Correcto: el emisor encamina el evento (como en HL7 v2 ADT/ORU) sin acoplarse a los procesos internos del receptor.",
                    "C", "Solamente cuando se desea sustituir y corregir todos los defectos de la API REST.", "Como advierte la Ficha 2, ningún paradigma sustituye ni 'arregla' los defectos de otro; cumplen propósitos distintos.",
                    "D", "Exclusivamente para almacenar recursos estáticos en disco duro sin transmitirlos.", "Eso corresponde al almacenamiento persistente (Base de datos)."
            );

            createDefaultCardQuestion(savedDeck, targetTopic,
                    "Según la Ficha 5 sobre las capacidades del paradigma API REST en FHIR, ¿qué mecanismos adicionales incluye además de las operaciones CRUD y búsquedas?",
                    null,
                    "Tal como resume la Ficha 5, la API REST de FHIR contempla también el trabajo asíncrono (peticiones cuya respuesta se recoge más tarde) y consultas mediante GraphQL.",
                    "A", "Trabajo asíncrono y consultas con GraphQL.", "Correcto: el paradigma REST de FHIR incluye interacciones asíncronas (Async Bulk/Request) y soporte de GraphQL.",
                    "B", "Únicamente lecturas síncronas sin posibilidad de crear ni borrar recursos.", "La Ficha 4 confirma que permite crear, leer, modificar, borrar, buscar y ejecutar operaciones ($).",
                    "C", "Solo permite pedir datos (pull), pero nunca enviarlos (push).", "La Ficha 4 aclara explícitamente que sirve tanto para enviar datos (push) como para pedirlos (pull).",
                    "D", "Obliga a transmitir los datos en segmentos HL7 v2 separados por tuberías (|).", "Eso es sintaxis HL7 v2, no API REST de FHIR."
            );

            createDefaultCardQuestion(savedDeck, targetTopic,
                    "Atendiendo a la tabla comparativa de la Ficha 3, ¿qué forma de mover datos en FHIR se asemeja a «enviar un informe cerrado y firmado» para que una persona lo lea tal cual se emitió?",
                    "{\n  \"resourceType\": \"Bundle\",\n  \"type\": \"document\"\n}",
                    "En la Ficha 3 se resume que los Documentos (Documents) equivalen a enviar un informe clínico cerrado y firmado para que sea leído por una persona tal cual se emitió.",
                    "A", "Documentos (Documents).", "Correcto: los documentos FHIR representan un conjunto clínico coherente, cerrado y firmado.",
                    "B", "API REST.", "REST se asemeja a ir a una ventanilla a pedir o dejar datos concretos al momento.",
                    "C", "Servicios (Services / SOA).", "Los servicios se asemejan a departamentos de una empresa que se solicitan acciones entre sí.",
                    "D", "Base de datos (Persistent store).", "La base de datos equivale al archivador donde se guarda todo en formato FHIR."
            );
        } catch (Exception ignored) {
            // No bloquear el arranque si ocurre cualquier excepción en el seed inicial
        }
    }

    private void createDefaultCardQuestion(StudyMaterial material, SyllabusTopic topic,
                                           String stem, String snippet, String explanation,
                                           String label1, String text1, String reason1,
                                           String label2, String text2, String reason2,
                                           String label3, String text3, String reason3,
                                           String label4, String text4, String reason4) {
        Question q = new Question();
        q.setMaterial(material);
        q.setTopic(topic);
        if (material != null && material.getCertification() != null) {
            q.setCertification(material.getCertification());
        } else {
            q.setCertification(resolveCertificationForTopicOrFallback(topic != null ? topic.getId() : null));
        }
        if (material != null && material.getArea() != null) {
            q.setArea(material.getArea());
        } else {
            q.setArea(resolveAreaForTopic(topic != null ? topic.getId() : null));
        }
        q.setSource("MANUAL");
        q.setQuestionType("SINGLE_CHOICE");
        q.setDifficulty(2);
        q.setVerified(true);
        q.setStem(stem);
        q.setCodeSnippet(snippet);
        q.setExplanation(explanation);
        q.setFhirSpecUrl("https://hl7.org/fhir/R4/exchange-module.html");
        q = questionRepository.save(q);

        boolean c1 = reason1.startsWith("Correcto");
        boolean c2 = reason2.startsWith("Correcto");
        boolean c3 = reason3.startsWith("Correcto");
        boolean c4 = reason4.startsWith("Correcto");

        q.addOption(new QuestionOption(label1, text1, c1, reason1));
        q.addOption(new QuestionOption(label2, text2, c2, reason2));
        q.addOption(new QuestionOption(label3, text3, c3, reason3));
        q.addOption(new QuestionOption(label4, text4, c4, reason4));

        for (QuestionOption opt : q.getOptions()) {
            questionOptionRepository.save(opt);
        }
        srsService.ensureSrsState(q);
    }

    public void deleteMaterial(Long id) {
        StudyMaterial m = studyMaterialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + id));
        checkMaterialOwnershipOrAdmin(m);

        // Soft delete para conservar trazabilidad e intentos históricos asociados al material
        m.setDeleted(true);
        m.setDeletedAt(User.formatNow());
        studyMaterialRepository.save(m);

        if (auditService != null) {
            auditService.logUserAction(resolveCurrentUser(), "MATERIAL_DELETED", "MATERIAL",
                    String.valueOf(id), "Material eliminado lógicamente (soft-delete): " + m.getTitle(), "127.0.0.1");
        }
    }

    @Transactional(readOnly = true)
    public java.io.File getMaterialPhysicalFile(Long id) {
        StudyMaterial m = studyMaterialRepository.findById(id).orElse(null);
        if (m != null && m.getFilePath() != null) {
            java.io.File f = new java.io.File(m.getFilePath());
            if (f.exists()) return f;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPracticeInfo(Long materialId) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material de estudio no encontrado: " + materialId));

        long linkedCount = questionRepository.countByMaterialIdAndVerifiedTrue(materialId);
        int totalRelevant = (int) linkedCount;
        if (material.getTopic() != null && !"STUDY_CARDS".equalsIgnoreCase(material.getSourceType())) {
            int topicCount = questionRepository.findByTopicIdAndVerifiedTrue(material.getTopic().getId()).size();
            totalRelevant = Math.max((int) linkedCount, topicCount);
        }

        Map<String, Object> info = new HashMap<>();
        info.put("materialId", material.getId());
        info.put("title", material.getTitle());
        info.put("sourceType", material.getSourceType());
        info.put("topicId", material.getTopic() != null ? material.getTopic().getId() : null);
        info.put("topicName", material.getTopic() != null ? material.getTopic().getName() : "General");
        info.put("linkedQuestionsCount", linkedCount);
        info.put("totalRelevantCount", "STUDY_CARDS".equalsIgnoreCase(material.getSourceType()) ? linkedCount : Math.max(1, totalRelevant));
        return info;
    }

    public Question generateQuestionFromMaterial(Long materialId) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + materialId));

        Question q = new Question();
        q.setMaterial(material);
        q.setTopic(material.getTopic());
        q.setCertification(material.getCertification() != null ? material.getCertification() : resolveCertificationForTopicOrFallback(material.getTopic() != null ? material.getTopic().getId() : null));
        q.setArea(material.getArea() != null ? material.getArea() : resolveAreaForTopic(material.getTopic() != null ? material.getTopic().getId() : null));
        q.setOwner(resolveCurrentUser());
        q.setCreatedAt(User.formatNow());
        q.setDeleted(false);
        q.setSource("AI_GENERATED");
        q.setQuestionType("SINGLE_CHOICE");
        q.setDifficulty(2);
        q.setVerified(true);

        String title = material.getTitle() != null ? material.getTitle() : "Documento FHIR";
        String content = material.getContentText() != null ? material.getContentText().toLowerCase() : "";

        if (content.contains("search") || content.contains("búsqueda") || content.contains("_include") || content.contains("param")) {
            q.setStem("[Pregunta del Documento: " + title + "] En base a la especificación de búsquedas analizada en este documento, ¿cuál es el parámetro oficial para incluir recursos referenciados sin realizar consultas separadas al servidor?");
            q.setCodeSnippet("GET [base]/Observation?subject=Patient/123&_include=Observation:subject");
            q.setExplanation("El parámetro estándar _include permite traer recursos referenciados en una sola llamada REST, como se documenta en: " + title);
            q.setFhirSpecUrl("https://hl7.org/fhir/R4/search.html#include");

            q = questionRepository.save(q);
            q.addOption(new QuestionOption("A", "_join=Patient", false, "_join no es una sintaxis válida en FHIR REST."));
            q.addOption(new QuestionOption("B", "_include=Observation:subject", true, "Correcto: utiliza _include con la referencia de origen para embeber el recurso en el Bundle devuelto."));
            q.addOption(new QuestionOption("C", "_expand=all", false, "_expand se utiliza en ValueSets terminológicos, no en búsquedas REST."));
            q.addOption(new QuestionOption("D", "_elements=subject", false, "_elements filtra los campos de salida, no resuelve referencias externas."));

        } else if (content.contains("modifier") || content.contains("extension") || content.contains("perfil")) {
            q.setStem("[Pregunta del Documento: " + title + "] Según las reglas de conformidad descritas en este apunte, ¿cómo debe proceder un consumidor FHIR si recibe una 'modifierExtension' que su implementación no sabe interpretar?");
            q.setCodeSnippet("{\n  \"resourceType\": \"Condition\",\n  \"modifierExtension\": [{\n    \"url\": \"http://example.org/fhir/StructureDefinition/condition-refuted\",\n    \"valueBoolean\": true\n  }]\n}");
            q.setExplanation("Las reglas de extensibilidad de HL7 FHIR establecen que cualquier modifierExtension no reconocida debe forzar el rechazo (error) del recurso, pues altera el significado del mismo.");
            q.setFhirSpecUrl("https://hl7.org/fhir/R4/extensibility.html#modifierExtension");

            q = questionRepository.save(q);
            q.addOption(new QuestionOption("A", "Debe ignorar la extensión silenciosamente y procesar el resto del recurso.", false, "Eso sólo está permitido para extensiones normales, nunca para modifierExtension."));
            q.addOption(new QuestionOption("B", "DEBE rechazar el recurso y emitir un error de procesamiento.", true, "Correcto: un modifierExtension no comprendido invalida la interpretación segura del recurso."));
            q.addOption(new QuestionOption("C", "Debe transformar el recurso a formato XML y reintentar la lectura.", false, "El formato de serialización no exime de las reglas de conformidad."));
            q.addOption(new QuestionOption("D", "Debe guardar la extensión como texto libre en el campo text/narrative.", false, "No es una regla válida de FHIR."));

        } else if (content.contains("bundle") || content.contains("transacción") || content.contains("transaction") || content.contains("batch")) {
            q.setStem("[Pregunta del Documento: " + title + "] En relación con los tipos de Bundle explicados en esta documentación, ¿cuál es la garantía esencial que ofrece un Bundle de tipo 'transaction'?");
            q.setCodeSnippet("{\n  \"resourceType\": \"Bundle\",\n  \"type\": \"transaction\"\n}");
            q.setExplanation("Un Bundle 'transaction' garantiza atomicidad (ACID): o se ejecutan todas las operaciones contenidas con éxito o se revierte completamente la transacción si una falla.");
            q.setFhirSpecUrl("https://hl7.org/fhir/R4/bundle.html");

            q = questionRepository.save(q);
            q.addOption(new QuestionOption("A", "Garantiza atomicidad: si una sola entrada falla, se realiza rollback de todas las operaciones.", true, "Correcto: las transacciones son atómicas e indivisibles."));
            q.addOption(new QuestionOption("B", "Procesa las operaciones en segundo plano sin devolver respuesta al cliente.", false, "Las transacciones son síncronas."));
            q.addOption(new QuestionOption("C", "Permite únicamente operaciones de lectura (GET) sin modificar datos.", false, "Las transacciones admiten operaciones CRUD completas."));
            q.addOption(new QuestionOption("D", "Limita el número total de recursos a un máximo de 5 por bloque.", false, "No existe dicho límite en el estándar FHIR."));

        } else {
            String topicName = material.getTopic() != null ? material.getTopic().getName() : "FHIR R4";
            q.setStem("[Pregunta del Documento: " + title + "] Atendiendo a los principios expuestos en este material sobre " + topicName + ", ¿cuál de los siguientes enunciados representa una mejor práctica oficial en HL7 FHIR R4?");
            q.setCodeSnippet(null);
            q.setExplanation("Pregunta de consolidación basada en los conceptos analizados en: " + title + ".");
            q.setFhirSpecUrl(material.getReferenceUrlOrPage() != null && material.getReferenceUrlOrPage().startsWith("http") ? material.getReferenceUrlOrPage() : "https://hl7.org/fhir/R4/");

            q = questionRepository.save(q);
            q.addOption(new QuestionOption("A", "Priorizar el uso de recursos y elementos estándar antes de crear extensiones complejas.", true, "Correcto: el principio de 80/20 de FHIR recomienda reutilizar siempre los recursos núcleo estándar."));
            q.addOption(new QuestionOption("B", "Modificar las etiquetas XML/JSON canónicas para ajustarse al lenguaje del hospital.", false, "Invalida la interoperabilidad estándar de FHIR."));
            q.addOption(new QuestionOption("C", "Omitir el elemento 'id' en recursos persistidos en el repositorio.", false, "Todo recurso persistido debe tener identificador lógico único."));
            q.addOption(new QuestionOption("D", "Eliminar el bloque 'meta' para reducir el consumo de ancho de banda.", false, "Los metadatos son cruciales para versionado y perfiles."));
        }

        for (QuestionOption opt : q.getOptions()) {
            questionOptionRepository.save(opt);
        }

        srsService.ensureSrsState(q);
        return q;
    }

    /**
     * Obtiene las estadísticas consolidadas, el histórico de evolución para gráficos
     * y el desglose de rendimiento por pregunta para un documento específico.
     */
    @Transactional(readOnly = true)
    public MaterialStatsDTO getMaterialStats(Long materialId) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material de estudio no encontrado: " + materialId));

        MaterialStatsDTO dto = new MaterialStatsDTO();
        dto.setMaterialId(material.getId());
        dto.setMaterialTitle(material.getTitle());
        dto.setSourceType(material.getSourceType());
        dto.setTopicId(material.getTopic() != null ? material.getTopic().getId() : null);
        dto.setTopicName(material.getTopic() != null ? material.getTopic().getName() : "General");

        long linkedCount = questionRepository.countByMaterialIdAndVerifiedTrue(materialId);
        dto.setLinkedQuestionsCount(linkedCount);

        // 1. Obtener intentos completados asociados a este material para el usuario actual (ordenados cronológicamente)
        Long currentUserId = resolveCurrentUser().getId();
        List<ExamAttempt> attempts = (securityContextHelper != null)
                ? attemptRepository.findFinishedAttemptsForMaterialAndUser(materialId, currentUserId)
                : attemptRepository.findFinishedAttemptsForMaterial(materialId);
        dto.setTotalAttempts(attempts.size());

        if (!attempts.isEmpty()) {
            int passedCount = 0;
            double sumScore = 0.0;
            double maxScore = 0.0;
            int totalQuestionsCount = 0;
            int totalCorrectCount = 0;
            long totalDurationSec = 0;

            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", new Locale("es", "ES"));

            int attemptIndex = 1;
            for (ExamAttempt a : attempts) {
                double score = a.getScorePercent() != null ? a.getScorePercent() : 0.0;
                sumScore += score;
                if (score > maxScore) maxScore = score;
                boolean passed = score >= 80.0;
                if (passed) passedCount++;

                int correctInAttempt = 0;
                int totalInAttempt = 0;
                int durationInAttempt = 0;

                if (a.getAnswers() != null && !a.getAnswers().isEmpty()) {
                    totalInAttempt = a.getAnswers().size();
                    for (ExamAttemptAnswer ans : a.getAnswers()) {
                        if (Boolean.TRUE.equals(ans.getCorrect())) {
                            correctInAttempt++;
                        }
                        if (ans.getTimeSpentSeconds() != null) {
                            durationInAttempt += ans.getTimeSpentSeconds();
                        }
                    }
                }

                if (durationInAttempt == 0 && a.getStartedAt() != null && a.getFinishedAt() != null) {
                    try {
                        durationInAttempt = (int) Math.max(1, java.time.Duration.between(a.getStartedAt(), a.getFinishedAt()).getSeconds());
                    } catch (Exception ignored) {}
                }

                totalQuestionsCount += totalInAttempt;
                totalCorrectCount += correctInAttempt;
                totalDurationSec += durationInAttempt;

                String dateLabel = "#" + attemptIndex;
                if (a.getStartedAt() != null) {
                    try {
                        dateLabel = a.getStartedAt().format(displayFormatter);
                    } catch (Exception ignored) {}
                }

                MaterialEvolutionPointDTO point = new MaterialEvolutionPointDTO(
                        a.getId(),
                        attemptIndex,
                        a.getStartedAt() != null ? a.getStartedAt().toString() : "",
                        a.getFinishedAt() != null ? a.getFinishedAt().toString() : "",
                        dateLabel,
                        a.getMode(),
                        Math.round(score * 10.0) / 10.0,
                        correctInAttempt,
                        totalInAttempt,
                        durationInAttempt,
                        passed
                );
                dto.getEvolution().add(point);
                attemptIndex++;
            }

            dto.setPassedAttempts(passedCount);
            dto.setFailedAttempts(attempts.size() - passedCount);
            dto.setPassRatePercent(Math.round(((double) passedCount / attempts.size()) * 1000.0) / 10.0);
            dto.setAverageScore(Math.round((sumScore / attempts.size()) * 10.0) / 10.0);
            dto.setBestScore(Math.round(maxScore * 10.0) / 10.0);
            dto.setLatestScore(dto.getEvolution().get(dto.getEvolution().size() - 1).getScorePercent());
            dto.setTotalQuestionsAnswered(totalQuestionsCount);
            dto.setTotalCorrectAnswers(totalCorrectCount);
            dto.setOverallAccuracyPercent(totalQuestionsCount > 0
                    ? Math.round(((double) totalCorrectCount / totalQuestionsCount) * 1000.0) / 10.0
                    : 0.0);
            dto.setAverageTimeSeconds((int) (totalDurationSec / attempts.size()));
        }

        // 2. Desglose de preguntas del documento y su dominio para el usuario actual
        List<Question> linkedQuestions = questionRepository.findByMaterialIdAndVerifiedTrue(materialId);
        List<ExamAttemptAnswer> materialAnswers = (securityContextHelper != null)
                ? attemptAnswerRepository.findAnswersByMaterialIdAndUserId(materialId, currentUserId)
                : attemptAnswerRepository.findAnswersByMaterialId(materialId);

        Map<Long, List<ExamAttemptAnswer>> answersByQuestionId = materialAnswers.stream()
                .filter(ans -> ans.getQuestion() != null && ans.getQuestion().getId() != null)
                .collect(Collectors.groupingBy(ans -> ans.getQuestion().getId()));

        for (Question q : linkedQuestions) {
            List<ExamAttemptAnswer> qAnswers = answersByQuestionId.getOrDefault(q.getId(), Collections.emptyList());
            int answeredCount = qAnswers.size();
            int correctCount = (int) qAnswers.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
            double acc = answeredCount > 0 ? Math.round(((double) correctCount / answeredCount) * 1000.0) / 10.0 : 0.0;

            String status = "SIN_INTENTOS";
            if (answeredCount > 0) {
                if (acc >= 80.0) {
                    status = "DOMINADA";
                } else if (acc >= 50.0) {
                    status = "EN_PROGRESO";
                } else {
                    status = "A_REFORZAR";
                }
            }

            dto.getQuestionPerformance().add(new MaterialQuestionPerformanceDTO(
                    q.getId(),
                    q.getStem(),
                    answeredCount,
                    correctCount,
                    acc,
                    status
            ));
        }

        return dto;
    }
}
