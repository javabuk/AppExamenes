package com.fhir.examapp.service;

import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.repository.QuestionOptionRepository;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.StudyMaterialRepository;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final SyllabusTopicRepository syllabusTopicRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final SpacedRepetitionService srsService;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository,
                                SyllabusTopicRepository syllabusTopicRepository,
                                QuestionRepository questionRepository,
                                QuestionOptionRepository questionOptionRepository,
                                SpacedRepetitionService srsService) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.syllabusTopicRepository = syllabusTopicRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.srsService = srsService;
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

        return studyMaterialRepository.save(material);
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

        return studyMaterialRepository.save(material);
    }

    /**
     * Guarda e indexa un archivo de apuntes en formato Markdown (.md, .markdown, .txt).
     */
    public StudyMaterial saveMarkdownFile(MultipartFile file, Long topicId, String customTitle) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        String title = (customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle.trim()
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "Apuntes Markdown");

        String contentText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (contentText.isBlank()) {
            contentText = "(Archivo Markdown vacío)";
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
                "MARKDOWN",
                title,
                contentText,
                safeOriginal
        );
        material.setFilePath(targetFile.getPath());

        return studyMaterialRepository.save(material);
    }

    /**
     * Guarda e indexa contenido de apuntes escrito directamente en formato Markdown.
     */
    public StudyMaterial saveMarkdownText(Long topicId, String title, String markdownText) throws IOException {
        SyllabusTopic topic = syllabusTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema de temario no encontrado: " + topicId));

        if (title == null || title.trim().isEmpty()) {
            title = "Apuntes Markdown";
        } else {
            title = title.trim();
        }

        if (markdownText == null || markdownText.trim().isEmpty()) {
            throw new IllegalArgumentException("El contenido Markdown no puede estar vacío.");
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
                "MARKDOWN",
                title,
                markdownText,
                safeTitle
        );
        material.setFilePath(targetFile.getPath());

        return studyMaterialRepository.save(material);
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
        return studyMaterialRepository.save(material);
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> getMaterialsByTopic(Long topicId) {
        return studyMaterialRepository.findByTopicId(topicId);
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> searchMaterials(String query) {
        if (query == null || query.trim().isEmpty()) {
            return studyMaterialRepository.findAll();
        }
        return studyMaterialRepository.searchMaterials(query.trim());
    }

    @Transactional(readOnly = true)
    public List<StudyMaterial> getAllMaterials() {
        return studyMaterialRepository.findAll();
    }

    @Transactional(readOnly = true)
    public StudyMaterial getMaterialById(Long id) {
        return studyMaterialRepository.findById(id).orElse(null);
    }

    public void deleteMaterial(Long id) {
        studyMaterialRepository.findById(id).ifPresent(m -> {
            if (m.getFilePath() != null) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(m.getFilePath()));
                } catch (Exception ignored) {}
            }
            studyMaterialRepository.delete(m);
        });
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
        if (material.getTopic() != null) {
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
        info.put("totalRelevantCount", Math.max(1, totalRelevant));
        return info;
    }

    public Question generateQuestionFromMaterial(Long materialId) {
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material no encontrado: " + materialId));

        Question q = new Question();
        q.setMaterial(material);
        q.setTopic(material.getTopic());
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
}
