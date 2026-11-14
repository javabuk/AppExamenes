package com.fhir.examapp.service;

import com.fhir.examapp.domain.*;
import com.fhir.examapp.dto.*;
import com.fhir.examapp.repository.*;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExamEngineService {

    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final SyllabusTopicRepository topicRepository;
    private final SpacedRepetitionService srsService;
    private final StudyMaterialRepository studyMaterialRepository;
    private final SecurityContextHelper securityContextHelper;
    private CertificationContextHelper certificationContextHelper;
    private CertificationRepository certificationRepository;
    private CertificationAreaRepository certificationAreaRepository;

    @Autowired
    public void setCertificationComponents(@Autowired(required = false) CertificationContextHelper certificationContextHelper,
                                          @Autowired(required = false) CertificationRepository certificationRepository,
                                          @Autowired(required = false) CertificationAreaRepository certificationAreaRepository) {
        this.certificationContextHelper = certificationContextHelper;
        this.certificationRepository = certificationRepository;
        this.certificationAreaRepository = certificationAreaRepository;
    }

    public ExamEngineService(ExamAttemptRepository attemptRepository,
                             ExamAttemptAnswerRepository attemptAnswerRepository,
                             QuestionRepository questionRepository,
                             SyllabusTopicRepository topicRepository,
                             SpacedRepetitionService srsService,
                             StudyMaterialRepository studyMaterialRepository) {
        this(attemptRepository, attemptAnswerRepository, questionRepository,
                topicRepository, srsService, studyMaterialRepository, null);
    }

    @Autowired
    public ExamEngineService(ExamAttemptRepository attemptRepository,
                             ExamAttemptAnswerRepository attemptAnswerRepository,
                             QuestionRepository questionRepository,
                             SyllabusTopicRepository topicRepository,
                             SpacedRepetitionService srsService,
                             StudyMaterialRepository studyMaterialRepository,
                             SecurityContextHelper securityContextHelper) {
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.srsService = srsService;
        this.studyMaterialRepository = studyMaterialRepository;
        this.securityContextHelper = securityContextHelper;
    }

    private User resolveCurrentUser() {
        if (securityContextHelper != null) {
            return securityContextHelper.getCurrentUser();
        }
        User fallback = new User("admin", "admin@fhir.local", "Admin", "", Role.ADMIN);
        fallback.setId(1L);
        return fallback;
    }

    private boolean isCurrentUserAdmin() {
        return securityContextHelper != null && securityContextHelper.isCurrentUserAdmin();
    }

    private Certification resolveActiveCertification(Long explicitCertId) {
        if (certificationContextHelper != null) {
            try {
                return certificationContextHelper.resolveActiveCertification(explicitCertId);
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                // If resolving active cert fails (e.g. tests without full setup), fallback gracefully
                if (certificationRepository != null) {
                    if (explicitCertId != null) {
                        return certificationRepository.findById(explicitCertId).orElse(null);
                    }
                    return certificationRepository.findById(1L).orElse(null);
                }
            }
        }
        if (certificationRepository != null) {
            if (explicitCertId != null) {
                return certificationRepository.findById(explicitCertId).orElse(null);
            }
            return certificationRepository.findById(1L).orElse(null);
        }
        return null;
    }

    /**
     * Inicia una sesión de examen en cualquiera de los 4 modos oficiales para el usuario autenticado.
     */
    public ExamSessionDTO startExam(ExamStartRequest request) {
        User currentUser = resolveCurrentUser();
        Long userId = currentUser.getId();
        Certification cert = resolveActiveCertification(request != null ? request.getCertificationId() : null);

        String mode = request != null && request.getMode() != null ? request.getMode().toUpperCase() : "SIMULATION";
        int totalQuestionsNeeded;
        if (request != null && request.getQuestionCount() != null && request.getQuestionCount() > 0) {
            totalQuestionsNeeded = request.getQuestionCount();
        } else if ("SIMULATION".equalsIgnoreCase(mode) && cert != null && cert.getExamQuestionCount() > 0) {
            totalQuestionsNeeded = cert.getExamQuestionCount();
        } else {
            totalQuestionsNeeded = 10;
        }

        List<Question> selectedQuestions;
        String materialTitle = null;
        StudyMaterial targetMaterial = null;

        Long requestedTopicId = request != null ? request.getTopicId() : null;
        Long requestedMaterialId = request != null ? request.getMaterialId() : null;

        if (requestedMaterialId != null) {
            StudyMaterial mat = studyMaterialRepository.findById(requestedMaterialId).orElse(null);
            if (mat != null && !Boolean.TRUE.equals(mat.getDeleted())) {
                targetMaterial = mat;
                materialTitle = mat.getTitle();
                selectedQuestions = selectQuestionsForMaterial(mat, mode, totalQuestionsNeeded);
            } else {
                selectedQuestions = selectQuestionsForMode(mode, totalQuestionsNeeded, requestedTopicId, userId, cert);
            }
        } else {
            selectedQuestions = selectQuestionsForMode(mode, totalQuestionsNeeded, requestedTopicId, userId, cert);
        }

        if (selectedQuestions.isEmpty()) {
            if (cert != null) {
                selectedQuestions = questionRepository.findByCertificationIdAndVerifiedTrue(cert.getId());
            } else {
                selectedQuestions = questionRepository.findByVerifiedTrue();
            }
            if (selectedQuestions.size() > totalQuestionsNeeded) {
                Collections.shuffle(selectedQuestions);
                selectedQuestions = selectedQuestions.subList(0, totalQuestionsNeeded);
            }
        }

        if (selectedQuestions.isEmpty()) {
            throw new IllegalStateException("No hay preguntas disponibles para la certificación o filtro seleccionado.");
        }

        selectedQuestions = new ArrayList<>(selectedQuestions);
        Collections.shuffle(selectedQuestions);

        // Crear intento en BBDD asociado al usuario autenticado y certificación
        ExamAttempt attempt = new ExamAttempt(mode, currentUser);
        if (targetMaterial != null) {
            attempt.setMaterial(targetMaterial);
        }
        if (cert != null) {
            attempt.setCertification(cert);
        }
        attempt = attemptRepository.save(attempt);

        ExamSessionDTO session = new ExamSessionDTO();
        session.setAttemptId(attempt.getId());
        session.setMode(mode);
        session.setStartedAt(attempt.getStartedAt());
        session.setTotalQuestions(selectedQuestions.size());
        session.setMaterialId(requestedMaterialId);
        session.setMaterialTitle(materialTitle);
        if (cert != null) {
            session.setCertificationId(cert.getId());
            session.setCertificationCode(cert.getCode());
            session.setCertificationName(cert.getName());
        }

        if ("SIMULATION".equalsIgnoreCase(mode)) {
            if (cert != null && cert.getExamDurationMinutes() > 0) {
                session.setTimeLimitMinutes(cert.getExamDurationMinutes());
            } else {
                session.setTimeLimitMinutes(Math.max(5, (int) Math.round(selectedQuestions.size() * 1.8)));
            }
        }

        boolean isTutor = "TUTOR".equalsIgnoreCase(mode);

        List<ExamQuestionDTO> questionDTOs = new ArrayList<>();
        for (Question q : selectedQuestions) {
            ExamQuestionDTO qDto = new ExamQuestionDTO();
            qDto.setId(q.getId());
            qDto.setTopicId(q.getTopic() != null ? q.getTopic().getId() : null);
            qDto.setTopicCode(q.getTopic() != null ? q.getTopic().getCode() : "");
            qDto.setTopicName(q.getTopic() != null ? q.getTopic().getName() : "");
            qDto.setMaterialId(q.getMaterial() != null ? q.getMaterial().getId() : null);
            qDto.setMaterialTitle(q.getMaterial() != null ? q.getMaterial().getTitle() : null);
            qDto.setQuestionType(q.getQuestionType());
            qDto.setDifficulty(q.getDifficulty());
            qDto.setStem(q.getStem());
            qDto.setCodeSnippet(q.getCodeSnippet());

            if (isTutor) {
                qDto.setExplanation(q.getExplanation());
                qDto.setFhirSpecUrl(q.getFhirSpecUrl());
                for (QuestionOption opt : q.getOptions()) {
                    if (Boolean.TRUE.equals(opt.getCorrect())) {
                        qDto.setCorrectOptionLabel(opt.getOptionLabel());
                        break;
                    }
                }
            }

            List<ExamOptionDTO> optionDTOs = q.getOptions().stream()
                    .map(o -> isTutor
                            ? new ExamOptionDTO(o.getId(), o.getOptionLabel(), o.getOptionText(), o.getCorrect(), o.getDistractorReason())
                            : new ExamOptionDTO(o.getId(), o.getOptionLabel(), o.getOptionText()))
                    .collect(Collectors.toList());

            qDto.setOptions(optionDTOs);
            questionDTOs.add(qDto);
        }

        session.setQuestions(questionDTOs);
        return session;
    }

    /**
     * Evalúa y finaliza el examen, verificando que el intento pertenezca al usuario autenticado (anti-IDOR).
     */
    public ExamResultDTO submitExam(ExamSubmitRequest request) {
        User currentUser = resolveCurrentUser();
        ExamAttempt attempt = attemptRepository.findById(request.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Intento de examen no encontrado: " + request.getAttemptId()));

        if (attempt.getUser() != null && attempt.getUser().getId() != null
                && !attempt.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No tiene permiso para enviar ni modificar el intento de examen de otro usuario.");
        }

        if (attempt.getUser() == null) {
            attempt.setUser(currentUser);
        }

        attempt.setFinishedAt(LocalDateTime.now());

        Map<Long, ExamAnswerSubmissionDTO> answersByQuestionId = new LinkedHashMap<>();
        if (request.getAnswers() != null) {
            for (ExamAnswerSubmissionDTO ans : request.getAnswers()) {
                answersByQuestionId.put(ans.getQuestionId(), ans);
            }
        }

        int totalCount = answersByQuestionId.size();
        int correctCount = 0;
        int totalSeconds = 0;
        List<QuestionReviewDTO> reviews = new ArrayList<>();

        for (Map.Entry<Long, ExamAnswerSubmissionDTO> entry : answersByQuestionId.entrySet()) {
            Long questionId = entry.getKey();
            ExamAnswerSubmissionDTO sub = entry.getValue();

            Question q = questionRepository.findById(questionId).orElse(null);
            if (q == null) continue;

            String selectedOption = sub.getSelectedOptions() != null ? sub.getSelectedOptions().trim().toUpperCase() : "";
            int timeSpent = sub.getTimeSpentSeconds() != null ? sub.getTimeSpentSeconds() : 0;
            totalSeconds += timeSpent;
            boolean flagged = Boolean.TRUE.equals(sub.getFlagged());

            String correctOptionLabel = "";
            for (QuestionOption opt : q.getOptions()) {
                if (Boolean.TRUE.equals(opt.getCorrect())) {
                    correctOptionLabel = opt.getOptionLabel().toUpperCase();
                    break;
                }
            }

            boolean isCorrect = correctOptionLabel.equalsIgnoreCase(selectedOption);
            if (isCorrect) {
                correctCount++;
            }

            ExamAttemptAnswer attemptAnswer = new ExamAttemptAnswer(
                    q, selectedOption, isCorrect, timeSpent, flagged
            );
            attempt.addAnswer(attemptAnswer);
            attemptAnswerRepository.save(attemptAnswer);

            // Actualizar repetición espaciada SM-2 para el usuario autenticado
            srsService.recordAnswerResult(currentUser.getId(), q, isCorrect);

            reviews.add(buildQuestionReviewDTO(q, selectedOption, correctOptionLabel, isCorrect, flagged, timeSpent));
        }

        double scorePercent = totalCount > 0 ? ((double) correctCount / totalCount) * 100.0 : 0.0;
        attempt.setScorePercent(Math.round(scorePercent * 10.0) / 10.0);
        attemptRepository.save(attempt);

        double passingCutoff = (attempt.getCertification() != null && attempt.getCertification().getPassingScorePercent() > 0)
                ? attempt.getCertification().getPassingScorePercent()
                : 70.0;

        ExamResultDTO result = new ExamResultDTO();
        result.setAttemptId(attempt.getId());
        result.setMode(attempt.getMode());
        result.setStartedAt(attempt.getStartedAt());
        result.setFinishedAt(attempt.getFinishedAt());
        result.setScorePercent(attempt.getScorePercent());
        result.setCorrectCount(correctCount);
        result.setTotalCount(totalCount);
        result.setTotalTimeSeconds(totalSeconds);
        result.setPassed(scorePercent >= passingCutoff);
        result.setPassingScorePercent(passingCutoff);
        if (attempt.getCertification() != null) {
            result.setCertificationId(attempt.getCertification().getId());
            result.setCertificationCode(attempt.getCertification().getCode());
        }
        result.setReviews(reviews);

        return result;
    }

    /**
     * Obtiene el detalle completo y corrección de un intento finalizado.
     * Verifica que el intento pertenezca al usuario autenticado o que sea ADMIN.
     */
    @Transactional(readOnly = true)
    public ExamResultDTO getAttemptReview(Long attemptId) {
        User currentUser = resolveCurrentUser();
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Intento de examen no encontrado: " + attemptId));

        if (attempt.getUser() != null && attempt.getUser().getId() != null
                && !attempt.getUser().getId().equals(currentUser.getId())
                && !isCurrentUserAdmin()) {
            throw new AccessDeniedException("No tiene permiso para consultar el intento de otro usuario.");
        }

        return buildExamResultFromAttempt(attempt);
    }

    @Transactional(readOnly = true)
    public ExamResultDTO buildExamResultFromAttempt(ExamAttempt attempt) {
        int totalCount = attempt.getAnswers() != null ? attempt.getAnswers().size() : 0;
        int correctCount = 0;
        int totalSeconds = 0;
        List<QuestionReviewDTO> reviews = new ArrayList<>();

        if (attempt.getAnswers() != null) {
            for (ExamAttemptAnswer ans : attempt.getAnswers()) {
                Question q = ans.getQuestion();
                if (q == null) continue;
                boolean isCorrect = Boolean.TRUE.equals(ans.getCorrect());
                if (isCorrect) correctCount++;
                int timeSpent = ans.getTimeSpentSeconds() != null ? ans.getTimeSpentSeconds() : 0;
                totalSeconds += timeSpent;

                String correctOptionLabel = "";
                for (QuestionOption opt : q.getOptions()) {
                    if (Boolean.TRUE.equals(opt.getCorrect())) {
                        correctOptionLabel = opt.getOptionLabel().toUpperCase();
                        break;
                    }
                }

                reviews.add(buildQuestionReviewDTO(
                        q,
                        ans.getSelectedOptions(),
                        correctOptionLabel,
                        isCorrect,
                        Boolean.TRUE.equals(ans.getFlagged()),
                        timeSpent
                ));
            }
        }

        if (totalSeconds == 0 && attempt.getStartedAt() != null && attempt.getFinishedAt() != null) {
            try {
                totalSeconds = (int) Math.max(1, Duration.between(attempt.getStartedAt(), attempt.getFinishedAt()).getSeconds());
            } catch (Exception ignored) {}
        }

        double scorePercent = attempt.getScorePercent() != null ? attempt.getScorePercent() : 0.0;
        double passingCutoff = (attempt.getCertification() != null && attempt.getCertification().getPassingScorePercent() > 0)
                ? attempt.getCertification().getPassingScorePercent()
                : 70.0;

        ExamResultDTO result = new ExamResultDTO();
        result.setAttemptId(attempt.getId());
        result.setMode(attempt.getMode());
        result.setStartedAt(attempt.getStartedAt());
        result.setFinishedAt(attempt.getFinishedAt());
        result.setScorePercent(scorePercent);
        result.setCorrectCount(correctCount);
        result.setTotalCount(totalCount);
        result.setTotalTimeSeconds(totalSeconds);
        result.setPassed(scorePercent >= passingCutoff);
        result.setPassingScorePercent(passingCutoff);
        if (attempt.getCertification() != null) {
            result.setCertificationId(attempt.getCertification().getId());
            result.setCertificationCode(attempt.getCertification().getCode());
        }
        result.setReviews(reviews);
        return result;
    }

    private QuestionReviewDTO buildQuestionReviewDTO(Question q, String selectedOption, String correctOptionLabel,
                                                     boolean isCorrect, boolean flagged, int timeSpent) {
        QuestionReviewDTO rev = new QuestionReviewDTO();
        rev.setQuestionId(q.getId());
        rev.setTopicName(q.getTopic() != null ? q.getTopic().getName() : "");
        rev.setStem(q.getStem());
        rev.setCodeSnippet(q.getCodeSnippet());
        rev.setSelectedOptions(selectedOption);
        rev.setCorrectOptionLabel(correctOptionLabel);
        rev.setCorrect(isCorrect);
        rev.setFlagged(flagged);
        rev.setTimeSpentSeconds(timeSpent);
        rev.setExplanation(q.getExplanation());
        rev.setFhirSpecUrl(q.getFhirSpecUrl());

        List<ExamOptionDTO> optionDTOs = q.getOptions().stream()
                .map(o -> new ExamOptionDTO(o.getId(), o.getOptionLabel(), o.getOptionText(), o.getCorrect(), o.getDistractorReason()))
                .collect(Collectors.toList());
        rev.setOptions(optionDTOs);
        return rev;
    }

    public List<Question> selectQuestionsForMaterial(StudyMaterial material, String mode, int count) {
        if (material == null) return Collections.emptyList();

        Long materialId = material.getId();

        List<Question> linked = questionRepository.findByMaterialIdAndVerifiedTrue(materialId);
        List<Question> result = new ArrayList<>(linked);
        Collections.shuffle(result);

        if ("STUDY_CARDS".equalsIgnoreCase(material.getSourceType()) && !result.isEmpty()) {
            return result.size() > count ? new ArrayList<>(result.subList(0, count)) : result;
        }

        Set<Long> pickedIds = linked.stream().map(Question::getId).collect(Collectors.toSet());
        Long certId = material.getCertification() != null ? material.getCertification().getId() : null;

        if (result.size() < count && (material.getArea() != null || material.getTopic() != null)) {
            List<Question> topicQuestions;
            if (certId != null) {
                Long areaOrTopicId = material.getArea() != null ? material.getArea().getId() : material.getTopic().getId();
                topicQuestions = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(certId, areaOrTopicId);
            } else {
                topicQuestions = questionRepository.findByTopicIdAndVerifiedTrue(material.getTopic().getId());
            }

            Set<String> keywords = extractKeywords((material.getTitle() != null ? material.getTitle() : "") + " " +
                                                   (material.getContentText() != null ? material.getContentText() : ""));

            topicQuestions.sort((q1, q2) -> {
                int score1 = calculateRelevanceScore(q1, keywords);
                int score2 = calculateRelevanceScore(q2, keywords);
                return Integer.compare(score2, score1);
            });

            for (Question q : topicQuestions) {
                if (result.size() >= count) break;
                if (!pickedIds.contains(q.getId())) {
                    result.add(q);
                    pickedIds.add(q.getId());
                }
            }
        }

        if (result.size() < count) {
            List<Question> remaining = certId != null
                    ? questionRepository.findByCertificationIdAndVerifiedTrue(certId)
                    : questionRepository.findByVerifiedTrue();
            Collections.shuffle(remaining);
            for (Question q : remaining) {
                if (result.size() >= count) break;
                if (!pickedIds.contains(q.getId())) {
                    result.add(q);
                    pickedIds.add(q.getId());
                }
            }
        }

        Collections.shuffle(result);
        return result.size() > count ? new ArrayList<>(result.subList(0, count)) : result;
    }

    private Set<String> extractKeywords(String text) {
        if (text == null) return Collections.emptySet();
        Set<String> stopWords = Set.of(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "en", "para", "por", "con", "sin",
            "este", "esta", "estos", "estas", "del", "al", "que", "se", "es", "son", "su", "sus", "como", "the",
            "and", "to", "of", "in", "for", "with", "on", "at", "by", "from", "an", "as", "is", "are", "or", "that",
            "http", "https", "org", "hl7", "fhir", "html"
        );
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_]+"))
                .filter(w -> w.length() >= 4)
                .filter(w -> !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    private int calculateRelevanceScore(Question q, Set<String> keywords) {
        int score = 0;
        String full = ((q.getStem() != null ? q.getStem() : "") + " " +
                       (q.getCodeSnippet() != null ? q.getCodeSnippet() : "") + " " +
                       (q.getExplanation() != null ? q.getExplanation() : "")).toLowerCase();
        for (String kw : keywords) {
            if (full.contains(kw)) {
                score++;
            }
        }
        return score;
    }

    private List<Question> selectQuestionsForMode(String mode, int count, Long topicIdFilter, Long userId) {
        return selectQuestionsForMode(mode, count, topicIdFilter, userId, null);
    }

    private List<Question> selectQuestionsForMode(String mode, int count, Long topicIdFilter, Long userId, Certification cert) {
        if (topicIdFilter != null) {
            List<Question> byTopic;
            if (cert != null) {
                byTopic = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(cert.getId(), topicIdFilter);
            } else {
                byTopic = questionRepository.findByTopicIdAndVerifiedTrue(topicIdFilter);
            }
            Collections.shuffle(byTopic);
            return byTopic.size() > count ? byTopic.subList(0, count) : byTopic;
        }

        switch (mode.toUpperCase()) {
            case "SIMULATION":
                return selectWeightedSimulationQuestions(count, cert);

            case "ADAPTIVE":
                return selectAdaptiveQuestions(count, userId, cert);

            case "ERRORS":
                List<Question> errors;
                if (cert != null) {
                    errors = questionRepository.findErrorInboxQuestionsByUserIdAndCertificationId(userId, cert.getId());
                    if (errors.isEmpty()) {
                        errors = questionRepository.findPreviouslyFailedQuestionsByUserIdAndCertificationId(userId, cert.getId());
                    }
                } else {
                    errors = questionRepository.findErrorInboxQuestionsByUserId(userId);
                    if (errors.isEmpty()) {
                        errors = questionRepository.findPreviouslyFailedQuestionsByUserId(userId);
                    }
                }
                Collections.shuffle(errors);
                return errors.size() > count ? errors.subList(0, count) : errors;

            case "TUTOR":
            default:
                List<Question> all = (cert != null)
                        ? questionRepository.findByCertificationIdAndVerifiedTrue(cert.getId())
                        : questionRepository.findByVerifiedTrue();
                Collections.shuffle(all);
                return all.size() > count ? all.subList(0, count) : all;
        }
    }

    private List<Question> selectWeightedSimulationQuestions(int totalCount) {
        return selectWeightedSimulationQuestions(totalCount, null);
    }

    private List<Question> selectWeightedSimulationQuestions(int totalCount, Certification cert) {
        List<Question> result = new ArrayList<>();
        Set<Long> pickedIds = new HashSet<>();

        if (cert != null && certificationAreaRepository != null) {
            List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdAndActiveTrueOrderBySortOrderAsc(cert.getId());
            if (!areas.isEmpty()) {
                for (CertificationArea area : areas) {
                    double weight = area.getWeightPercent() > 0 ? area.getWeightPercent() : (100.0 / areas.size());
                    int countForArea = (int) Math.max(1, Math.round((weight / 100.0) * totalCount));

                    List<Question> areaQuestions = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(cert.getId(), area.getId());
                    Collections.shuffle(areaQuestions);

                    for (Question q : areaQuestions) {
                        if (result.size() < totalCount && !pickedIds.contains(q.getId()) && countForArea > 0) {
                            result.add(q);
                            pickedIds.add(q.getId());
                            countForArea--;
                        }
                    }
                }
            }
        } else {
            List<SyllabusTopic> topics = topicRepository.findAll();
            for (SyllabusTopic topic : topics) {
                double weight = topic.getExamWeightPercent() != null ? topic.getExamWeightPercent() : 15.0;
                int countForTopic = (int) Math.max(1, Math.round((weight / 100.0) * totalCount));

                List<Question> topicQuestions = questionRepository.findByTopicIdAndVerifiedTrue(topic.getId());
                Collections.shuffle(topicQuestions);

                for (Question q : topicQuestions) {
                    if (result.size() < totalCount && !pickedIds.contains(q.getId()) && countForTopic > 0) {
                        result.add(q);
                        pickedIds.add(q.getId());
                        countForTopic--;
                    }
                }
            }
        }

        if (result.size() < totalCount) {
            List<Question> remaining = (cert != null)
                    ? questionRepository.findByCertificationIdAndVerifiedTrue(cert.getId())
                    : questionRepository.findByVerifiedTrue();
            Collections.shuffle(remaining);
            for (Question q : remaining) {
                if (result.size() >= totalCount) break;
                if (!pickedIds.contains(q.getId())) {
                    result.add(q);
                    pickedIds.add(q.getId());
                }
            }
        }

        Collections.shuffle(result);
        return result;
    }

    private List<Question> selectAdaptiveQuestions(int totalCount, Long userId) {
        return selectAdaptiveQuestions(totalCount, userId, null);
    }

    private List<Question> selectAdaptiveQuestions(int totalCount, Long userId, Certification cert) {
        int weakCount = (int) Math.round(totalCount * 0.60);
        int failedCount = (int) Math.round(totalCount * 0.25);
        int newCount = totalCount - weakCount - failedCount;

        List<Question> selected = new ArrayList<>();
        Set<Long> pickedIds = new HashSet<>();

        if (cert != null && certificationAreaRepository != null) {
            List<CertificationArea> areas = new ArrayList<>(certificationAreaRepository.findByCertificationIdAndActiveTrueOrderBySortOrderAsc(cert.getId()));
            for (CertificationArea a : areas) {
                List<Question> aQuestions = questionRepository.findByCertificationIdAndAreaIdAndVerifiedTrue(cert.getId(), a.getId());
                Collections.shuffle(aQuestions);
                for (Question q : aQuestions) {
                    if (selected.size() < weakCount && !pickedIds.contains(q.getId())) {
                        selected.add(q);
                        pickedIds.add(q.getId());
                    }
                }
                if (selected.size() >= weakCount) break;
            }
        } else {
            List<SyllabusTopic> topics = new ArrayList<>(topicRepository.findAll());
            topics.sort(Comparator.comparingDouble(t -> calculateTopicAccuracy(t, userId)));

            for (SyllabusTopic t : topics) {
                List<Question> tQuestions = questionRepository.findByTopicIdAndVerifiedTrue(t.getId());
                Collections.shuffle(tQuestions);
                for (Question q : tQuestions) {
                    if (selected.size() < weakCount && !pickedIds.contains(q.getId())) {
                        selected.add(q);
                        pickedIds.add(q.getId());
                    }
                }
                if (selected.size() >= weakCount) break;
            }
        }

        List<Question> failedQuestions = (cert != null)
                ? questionRepository.findPreviouslyFailedQuestionsByUserIdAndCertificationId(userId, cert.getId())
                : questionRepository.findPreviouslyFailedQuestionsByUserId(userId);
        Collections.shuffle(failedQuestions);
        int addedFailed = 0;
        for (Question q : failedQuestions) {
            if (addedFailed < failedCount && !pickedIds.contains(q.getId())) {
                selected.add(q);
                pickedIds.add(q.getId());
                addedFailed++;
            }
        }

        List<Question> newQuestions = (cert != null)
                ? questionRepository.findUnattemptedQuestionsByUserIdAndCertificationId(userId, cert.getId())
                : questionRepository.findUnattemptedQuestionsByUserId(userId);
        Collections.shuffle(newQuestions);
        int addedNew = 0;
        for (Question q : newQuestions) {
            if (addedNew < newCount && !pickedIds.contains(q.getId())) {
                selected.add(q);
                pickedIds.add(q.getId());
                addedNew++;
            }
        }

        if (selected.size() < totalCount) {
            List<Question> fallback = (cert != null)
                    ? questionRepository.findByCertificationIdAndVerifiedTrue(cert.getId())
                    : questionRepository.findByVerifiedTrue();
            Collections.shuffle(fallback);
            for (Question q : fallback) {
                if (selected.size() >= totalCount) break;
                if (!pickedIds.contains(q.getId())) {
                    selected.add(q);
                    pickedIds.add(q.getId());
                }
            }
        }

        Collections.shuffle(selected);
        return selected;
    }

    private double calculateTopicAccuracy(SyllabusTopic topic, Long userId) {
        long total = attemptAnswerRepository.countTotalByUserIdAndTopicId(userId, topic.getId());
        if (total == 0) return 0.0;
        long correct = attemptAnswerRepository.countCorrectByUserIdAndTopicId(userId, topic.getId());
        return (double) correct / total;
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getRecentAttempts() {
        return getRecentAttempts(null);
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getRecentAttempts(Long explicitCertId) {
        User currentUser = resolveCurrentUser();
        Certification cert = resolveActiveCertification(explicitCertId);
        if (cert != null) {
            return attemptRepository.findTop10ByUser_IdAndCertification_IdAndFinishedAtIsNotNullOrderByFinishedAtDesc(currentUser.getId(), cert.getId());
        }
        return attemptRepository.findTop10ByUser_IdAndFinishedAtIsNotNullOrderByFinishedAtDesc(currentUser.getId());
    }
}
