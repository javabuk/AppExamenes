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
