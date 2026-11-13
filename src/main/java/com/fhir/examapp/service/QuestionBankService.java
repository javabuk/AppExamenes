package com.fhir.examapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.domain.*;
import com.fhir.examapp.dto.QuestionCreateDTO;
import com.fhir.examapp.repository.QuestionOptionRepository;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.StudyMaterialRepository;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
public class QuestionBankService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final SyllabusTopicRepository topicRepository;
    private final StudyMaterialRepository materialRepository;
    private final SpacedRepetitionService srsService;
    private final ObjectMapper objectMapper;
    private final SecurityContextHelper securityContextHelper;
    private final AuditService auditService;

    public QuestionBankService(QuestionRepository questionRepository,
                               QuestionOptionRepository optionRepository,
                               SyllabusTopicRepository topicRepository,
                               StudyMaterialRepository materialRepository,
                               SpacedRepetitionService srsService,
                               ObjectMapper objectMapper) {
        this(questionRepository, optionRepository, topicRepository,
                materialRepository, srsService, objectMapper, null, null);
    }

    @Autowired
    public QuestionBankService(QuestionRepository questionRepository,
                               QuestionOptionRepository optionRepository,
                               SyllabusTopicRepository topicRepository,
                               StudyMaterialRepository materialRepository,
                               SpacedRepetitionService srsService,
                               ObjectMapper objectMapper,
                               SecurityContextHelper securityContextHelper,
                               AuditService auditService) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.topicRepository = topicRepository;
        this.materialRepository = materialRepository;
        this.srsService = srsService;
        this.objectMapper = objectMapper;
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

    private void checkOwnershipOrAdmin(Question question) {
        User currentUser = resolveCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (question.getOwner() != null && question.getOwner().getId() != null
                && question.getOwner().getId().equals(currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("No tiene permiso para modificar o eliminar una pregunta creada por otro usuario.");
    }

    public Question createQuestion(QuestionCreateDTO dto) {
        User currentUser = resolveCurrentUser();

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
        question.setOwner(currentUser);
        question.setCreatedAt(User.formatNow());
        question.setDeleted(false);
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

        srsService.ensureSrsState(currentUser.getId(), saved);
        return saved;
    }

    public Question updateQuestion(Long id, QuestionCreateDTO dto) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada: " + id));
        if (Boolean.TRUE.equals(question.getDeleted())) {
            throw new IllegalArgumentException("Pregunta no encontrada: " + id);
        }

        checkOwnershipOrAdmin(question);

        if (dto.getTopicId() != null) {
            SyllabusTopic topic = topicRepository.findById(dto.getTopicId())
                    .orElseThrow(() -> new IllegalArgumentException("Tema no encontrado: " + dto.getTopicId()));
            question.setTopic(topic);
        }
        if (dto.getMaterialId() != null) {
            StudyMaterial mat = materialRepository.findById(dto.getMaterialId()).orElse(null);
            question.setMaterial(mat);
        }
        if (dto.getStem() != null && !dto.getStem().isBlank()) {
            question.setStem(dto.getStem().trim());
        }
        if (dto.getCodeSnippet() != null) {
            question.setCodeSnippet(dto.getCodeSnippet());
        }
        if (dto.getExplanation() != null && !dto.getExplanation().isBlank()) {
            question.setExplanation(dto.getExplanation().trim());
        }
        if (dto.getFhirSpecUrl() != null) {
            question.setFhirSpecUrl(dto.getFhirSpecUrl());
        }
        if (dto.getDifficulty() != null) {
            question.setDifficulty(dto.getDifficulty());
        }
        if (dto.getQuestionType() != null && !dto.getQuestionType().isBlank()) {
            question.setQuestionType(dto.getQuestionType());
        }

        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            question.getOptions().clear();
            questionRepository.saveAndFlush(question);
            for (QuestionCreateDTO.OptionCreateDTO optDto : dto.getOptions()) {
                QuestionOption opt = new QuestionOption(
                        optDto.getOptionLabel(),
                        optDto.getOptionText(),
                        optDto.getCorrect() != null ? optDto.getCorrect() : false,
                        optDto.getDistractorReason()
                );
                question.addOption(opt);
                optionRepository.save(opt);
            }
        }

        question.setUpdatedAt(User.formatNow());
        Question saved = questionRepository.save(question);

        if (auditService != null) {
            auditService.logUserAction(resolveCurrentUser(), "QUESTION_UPDATED", "QUESTION",
                    String.valueOf(saved.getId()), "Pregunta editada: " + saved.getId(), "127.0.0.1");
        }
        return saved;
    }

    public Question generateMirrorVariant(Long baseQuestionId) {
        User currentUser = resolveCurrentUser();
        Question base = questionRepository.findById(baseQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta base no encontrada: " + baseQuestionId));

        Question variant = new Question();
        variant.setTopic(base.getTopic());
        variant.setParentQuestion(base);
        variant.setOwner(currentUser);
        variant.setCreatedAt(User.formatNow());
        variant.setDeleted(false);
        variant.setSource("AI_GENERATED");
        variant.setQuestionType(base.getQuestionType());
        variant.setDifficulty(base.getDifficulty());
        variant.setVerified(true);

        String topicCode = base.getTopic() != null ? base.getTopic().getCode() : "";

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

        srsService.ensureSrsState(currentUser.getId(), variant);
        return variant;
    }

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
        return questionRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsByTopic(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    @Transactional(readOnly = true)
    public List<Question> searchQuestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return questionRepository.findAllActive();
        }
        return questionRepository.searchByKeyword(query.trim());
    }

    public void deleteQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada: " + id));
        checkOwnershipOrAdmin(question);

        // Soft delete para no romper exam_attempt_answers históricos
        question.setDeleted(true);
        question.setDeletedAt(User.formatNow());
        question.setVerified(false);
        questionRepository.save(question);

        if (auditService != null) {
            auditService.logUserAction(resolveCurrentUser(), "QUESTION_DELETED", "QUESTION",
                    String.valueOf(id), "Pregunta eliminada lógicamente (soft-delete): " + id, "127.0.0.1");
        }
    }
}
