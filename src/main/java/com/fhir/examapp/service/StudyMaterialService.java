package com.fhir.examapp.service;

import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.repository.StudyMaterialRepository;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final SyllabusTopicRepository syllabusTopicRepository;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository, SyllabusTopicRepository syllabusTopicRepository) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.syllabusTopicRepository = syllabusTopicRepository;
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

        StudyMaterial material = new StudyMaterial(
                topic,
                "COURSE_PDF",
                title,
                extractedText,
                file.getOriginalFilename() + "#total"
        );

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
        studyMaterialRepository.deleteById(id);
    }
}
