package com.fhir.examapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.dto.QuestionCreateDTO;
import com.fhir.examapp.repository.QuestionOptionRepository;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.StudyMaterialRepository;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class QuestionBankService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final SyllabusTopicRepository topicRepository;
    private final StudyMaterialRepository materialRepository;
    private final SpacedRepetitionService srsService;
    private final ObjectMapper objectMapper;

    public QuestionBankService(QuestionRepository questionRepository,
                               QuestionOptionRepository optionRepository,
                               SyllabusTopicRepository topicRepository,
                               StudyMaterialRepository materialRepository,
                               SpacedRepetitionService srsService,
                               ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.topicRepository = topicRepository;
        this.materialRepository = materialRepository;
        this.srsService = srsService;
        this.objectMapper = objectMapper;
    }

    public Question createQuestion(QuestionCreateDTO dto) {
        StudyMaterial material = null;
        if (dto.getMaterialId() != null) {
            material = materialRepository.findById(dto.getMaterialId()).orElse(null);
        }

        final Long resolvedTopicId = (dto.getTopicId() != null)
                ? dto.getTopicId()
                : (material != null && material.getTopic() != null ? material.getTopic().getId() : null);

        if (resolvedTopicId == null) {
            throw new IllegalArgumentException("Debe especificarse un tema (topicId) o un documento asociado con tema válido.");
        }

        SyllabusTopic topic = topicRepository.findById(resolvedTopicId)
                .orElseThrow(() -> new IllegalArgumentException("Tema no encontrado: " + resolvedTopicId));

        Question question = new Question();
        question.setTopic(topic);
        question.setMaterial(material);
        question.setStem(dto.getStem());
        question.setCodeSnippet(dto.getCodeSnippet());
        question.setExplanation(dto.getExplanation());
        question.setFhirSpecUrl(dto.getFhirSpecUrl());
        question.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : 2);
        question.setQuestionType(dto.getQuestionType() != null ? dto.getQuestionType() : "SINGLE_CHOICE");
        question.setSource(dto.getSource() != null ? dto.getSource() : "MANUAL");
        question.setVerified(true);

        if (dto.getParentQuestionId() != null) {
            questionRepository.findById(dto.getParentQuestionId()).ifPresent(question::setParentQuestion);
        }

        Question saved = questionRepository.save(question);

        if (dto.getOptions() != null) {
            for (QuestionCreateDTO.OptionCreateDTO optDto : dto.getOptions()) {
                QuestionOption opt = new QuestionOption(
                        optDto.getOptionLabel(),
                        optDto.getOptionText(),
                        optDto.getCorrect() != null ? optDto.getCorrect() : false,
                        optDto.getDistractorReason()
                );
                saved.addOption(opt);
                optionRepository.save(opt);
            }
        }

        srsService.ensureSrsState(saved);
        return saved;
    }

    /**
     * Generador automático de preguntas "Espejo" y "Giro" (Clone & Twist):
     * Toma una pregunta existente y genera una variante cambiando el escenario clínico,
     * invirtiendo la condición lógica o evaluando el concepto complementario en FHIR.
     */
    public Question generateMirrorVariant(Long baseQuestionId) {
        Question base = questionRepository.findById(baseQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta base no encontrada: " + baseQuestionId));

        Question variant = new Question();
        variant.setTopic(base.getTopic());
        variant.setParentQuestion(base);
        variant.setSource("AI_GENERATED");
        variant.setQuestionType(base.getQuestionType());
        variant.setDifficulty(base.getDifficulty());
        variant.setVerified(true);

        String topicCode = base.getTopic() != null ? base.getTopic().getCode() : "";

        // Reglas de generación según el dominio FHIR
        if (base.getStem().contains("Observation") || topicCode.equals("REST_SEARCH")) {
            variant.setStem("[Variante Giro] Un sistema clínico necesita recuperar los recursos DiagnosticReport que referencien al paciente 'Patient/456' y traer en la misma respuesta los recursos Observation correspondientes a los resultados (result). ¿Cuál es la consulta FHIR correcta?");
            variant.setCodeSnippet("GET [base]/DiagnosticReport?subject=Patient/456&_include=DiagnosticReport:result");
            variant.setExplanation("Al igual que en Observation, en DiagnosticReport la relación con las observaciones analíticas se establece mediante la referencia 'result'. Por lo tanto, el parámetro estándar de inclusión es _include=DiagnosticReport:result.");
            variant.setFhirSpecUrl("https://hl7.org/fhir/R4/diagnosticreport.html");

            variant = questionRepository.save(variant);

            variant.addOption(new QuestionOption("A", "GET [base]/DiagnosticReport?patient=456&_expand=Observation", false, "El parámetro estándar es _include, no _expand."));
            variant.addOption(new QuestionOption("B", "GET [base]/DiagnosticReport?subject=Patient/456&_include=DiagnosticReport:result", true, "Correcto: utiliza _include con la referencia DiagnosticReport:result para traer las observaciones asociadas."));
            variant.addOption(new QuestionOption("C", "GET [base]/DiagnosticReport?subject=Patient/456&_join=result", false, "FHIR REST no soporta cláusulas _join."));
            variant.addOption(new QuestionOption("D", "GET [base]/Observation?report=DiagnosticReport/456&_include=all", false, "El parámetro no es _include=all y la consulta se hace sobre DiagnosticReport."));

        } else if (base.getStem().contains("modifierExtension") || topicCode.equals("PROFILING")) {
            variant.setStem("[Variante Giro] Un perfil nacional define una extensión para registrar si un procedimiento quirúrgico fue cancelado antes de anestesiar. Si un sistema cliente recibe el recurso con esta extensión pero no la reconoce, ¿cuándo estaría obligado por la especificación FHIR a rechazar el recurso?");
            variant.setCodeSnippet("{\n  \"resourceType\": \"Procedure\",\n  \"modifierExtension\": [{\n    \"url\": \"http://example.org/fhir/StructureDefinition/procedure-cancelled\",\n    \"valueBoolean\": true\n  }]\n}");
            variant.setExplanation("Si la extensión fue definida como 'modifierExtension', altera la interpretación básica del procedimiento (no se llevó a cabo). Cualquier sistema receptor que no entienda una modifierExtension DEBE rechazar el recurso obligatoriamente.");
            variant.setFhirSpecUrl("https://hl7.org/fhir/R4/extensibility.html#modifierExtension");

            variant = questionRepository.save(variant);

            variant.addOption(new QuestionOption("A", "Siempre que el recurso supere los 100 KB de tamaño.", false, "El tamaño no determina la obligación de rechazo."));
            variant.addOption(new QuestionOption("B", "Únicamente si la extensión está ubicada en modifierExtension y el sistema receptor no la comprende.", true, "Correcto: las modifierExtension no comprendidas fuerzan el rechazo del recurso."));
            variant.addOption(new QuestionOption("C", "Bajo ninguna circunstancia; los sistemas deben ignorar campos no reconocidos en FHIR.", false, "Eso aplica a extensiones normales, pero NUNCA a modifierExtension."));
            variant.addOption(new QuestionOption("D", "Solo si el servidor opera en modo XML en lugar de JSON.", false, "Las reglas de conformidad son idénticas en JSON y XML."));

        } else {
            // Variante genérica de refuerzo
            variant.setStem("[Variante Espejo] " + base.getStem().replace("¿Cuál es", "¿Cuál de las siguientes afirmaciones describe con mayor precisión"));
            variant.setCodeSnippet(base.getCodeSnippet());
            variant.setExplanation("Variante de consolidación del concepto: " + base.getExplanation());
            variant.setFhirSpecUrl(base.getFhirSpecUrl());

            variant = questionRepository.save(variant);

            for (QuestionOption opt : base.getOptions()) {
                variant.addOption(new QuestionOption(
                        opt.getOptionLabel(),
                        opt.getOptionText(),
                        opt.getCorrect(),
                        opt.getDistractorReason()
                ));
            }
        }

        for (QuestionOption opt : variant.getOptions()) {
            optionRepository.save(opt);
        }

        srsService.ensureSrsState(variant);
        return variant;
    }

    /**
     * Importación masiva de preguntas desde formato JSON con soporte de tema y material por defecto.
     */
    public int importQuestionsFromJson(String jsonContent) throws IOException {
        return importQuestionsFromJson(jsonContent, null, null);
    }

    public int importQuestionsFromJson(String jsonContent, Long defaultTopicId, Long defaultMaterialId) throws IOException {
        List<QuestionCreateDTO> dtoList = objectMapper.readValue(jsonContent, new TypeReference<List<QuestionCreateDTO>>() {});
        int count = 0;
        for (QuestionCreateDTO dto : dtoList) {
            if (dto.getTopicId() == null && defaultTopicId != null) {
                dto.setTopicId(defaultTopicId);
            }
            if (dto.getMaterialId() == null && defaultMaterialId != null) {
                dto.setMaterialId(defaultMaterialId);
            }
            createQuestion(dto);
            count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsByTopic(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    @Transactional(readOnly = true)
    public List<Question> searchQuestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return questionRepository.findAll();
        }
        return questionRepository.searchByKeyword(query.trim());
    }

    public void deleteQuestion(Long id) {
        questionRepository.deleteById(id);
    }
}
