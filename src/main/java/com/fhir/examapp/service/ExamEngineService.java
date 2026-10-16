package com.fhir.examapp.service;

import com.fhir.examapp.domain.*;
import com.fhir.examapp.dto.*;
import com.fhir.examapp.repository.*;
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

    public ExamEngineService(ExamAttemptRepository attemptRepository,
                             ExamAttemptAnswerRepository attemptAnswerRepository,
                             QuestionRepository questionRepository,
                             SyllabusTopicRepository topicRepository,
                             SpacedRepetitionService srsService) {
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.srsService = srsService;
    }

    /**
     * Inicia una sesión de examen en cualquiera de los 4 modos oficiales:
     * - SIMULATION: Ponderado por pesos del temario HL7 FHIR
     * - ADAPTIVE (Weak-Spot Hunter): 60% temas débiles, 25% falladas, 15% nuevas
     * - TUTOR: Retroalimentación inmediata
     * - ERRORS (Maratón de Fallos): Preguntas falladas sin 2 aciertos seguidos
     */
    public ExamSessionDTO startExam(ExamStartRequest request) {
        String mode = request.getMode() != null ? request.getMode().toUpperCase() : "SIMULATION";
        int totalQuestionsNeeded = request.getQuestionCount() != null && request.getQuestionCount() > 0 
                ? request.getQuestionCount() : 10;

        List<Question> selectedQuestions = selectQuestionsForMode(mode, totalQuestionsNeeded, request.getTopicId());

        if (selectedQuestions.isEmpty()) {
            // Si el modo específico no arrojó resultados (ej. no hay fallos en ERRORS), cargar banco general
            selectedQuestions = questionRepository.findByVerifiedTrue();
            if (selectedQuestions.size() > totalQuestionsNeeded) {
                Collections.shuffle(selectedQuestions);
                selectedQuestions = selectedQuestions.subList(0, totalQuestionsNeeded);
            }
        }

        // Crear intento en BBDD
        ExamAttempt attempt = new ExamAttempt(mode);
        attempt = attemptRepository.save(attempt);

        ExamSessionDTO session = new ExamSessionDTO();
        session.setAttemptId(attempt.getId());
        session.setMode(mode);
        session.setStartedAt(attempt.getStartedAt());
        session.setTotalQuestions(selectedQuestions.size());

        if ("SIMULATION".equalsIgnoreCase(mode)) {
            // En simulación estándar HL7, ~1.8 minutos por pregunta (50 preguntas en 90 min)
            session.setTimeLimitMinutes(Math.max(5, (int) Math.round(selectedQuestions.size() * 1.8)));
        }

        boolean isTutor = "TUTOR".equalsIgnoreCase(mode);

        List<ExamQuestionDTO> questionDTOs = new ArrayList<>();
        for (Question q : selectedQuestions) {
            ExamQuestionDTO qDto = new ExamQuestionDTO();
            qDto.setId(q.getId());
            qDto.setTopicId(q.getTopic() != null ? q.getTopic().getId() : null);
            qDto.setTopicCode(q.getTopic() != null ? q.getTopic().getCode() : "");
            qDto.setTopicName(q.getTopic() != null ? q.getTopic().getName() : "");
            qDto.setQuestionType(q.getQuestionType());
            qDto.setDifficulty(q.getDifficulty());
            qDto.setStem(q.getStem());
            qDto.setCodeSnippet(q.getCodeSnippet());

            // En modo Tutor se incluye la explicación y feedback inmediato
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
     * Evalúa y finaliza el examen, calculando la nota, actualizando SRS y generando desglose con distractor reasons.
     */
    public ExamResultDTO submitExam(ExamSubmitRequest request) {
        ExamAttempt attempt = attemptRepository.findById(request.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Intento de examen no encontrado: " + request.getAttemptId()));

        attempt.setFinishedAt(LocalDateTime.now());

        Map<Long, ExamAnswerSubmissionDTO> answersByQuestionId = new HashMap<>();
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

            // Encontrar opción correcta
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

            // Persistir respuesta
            ExamAttemptAnswer attemptAnswer = new ExamAttemptAnswer(
                    q, selectedOption, isCorrect, timeSpent, flagged
            );
            attempt.addAnswer(attemptAnswer);
            attemptAnswerRepository.save(attemptAnswer);

            // Actualizar repetición espaciada SM-2
            srsService.recordAnswerResult(q, isCorrect);

            // Crear DTO de revisión detallada
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

            reviews.add(rev);
        }

        double scorePercent = totalCount > 0 ? ((double) correctCount / totalCount) * 100.0 : 0.0;
        attempt.setScorePercent(Math.round(scorePercent * 10.0) / 10.0);
        attemptRepository.save(attempt);

        ExamResultDTO result = new ExamResultDTO();
        result.setAttemptId(attempt.getId());
        result.setMode(attempt.getMode());
        result.setStartedAt(attempt.getStartedAt());
        result.setFinishedAt(attempt.getFinishedAt());
        result.setScorePercent(attempt.getScorePercent());
        result.setCorrectCount(correctCount);
        result.setTotalCount(totalCount);
        result.setTotalTimeSeconds(totalSeconds);
        result.setPassed(scorePercent >= 80.0); // 80% umbral seguro FHIR
        result.setReviews(reviews);

        return result;
    }

    private List<Question> selectQuestionsForMode(String mode, int count, Long topicIdFilter) {
        if (topicIdFilter != null) {
            List<Question> byTopic = questionRepository.findByTopicIdAndVerifiedTrue(topicIdFilter);
            Collections.shuffle(byTopic);
            return byTopic.size() > count ? byTopic.subList(0, count) : byTopic;
        }

        switch (mode.toUpperCase()) {
            case "SIMULATION":
                return selectWeightedSimulationQuestions(count);

            case "ADAPTIVE":
                return selectAdaptiveQuestions(count);

            case "ERRORS":
                List<Question> errors = questionRepository.findErrorInboxQuestions();
                if (errors.isEmpty()) {
                    errors = questionRepository.findPreviouslyFailedQuestions();
                }
                Collections.shuffle(errors);
                return errors.size() > count ? errors.subList(0, count) : errors;

            case "TUTOR":
            default:
                List<Question> all = questionRepository.findByVerifiedTrue();
                Collections.shuffle(all);
                return all.size() > count ? all.subList(0, count) : all;
        }
    }

    /**
     * Selección para Modo Simulacro: Distribución ponderada por los pesos oficiales de HL7 FHIR.
     */
    private List<Question> selectWeightedSimulationQuestions(int totalCount) {
        List<SyllabusTopic> topics = topicRepository.findAll();
        List<Question> result = new ArrayList<>();
        Set<Long> pickedIds = new HashSet<>();

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

        // Rellenar si faltan para alcanzar la meta totalCount
        if (result.size() < totalCount) {
            List<Question> remaining = questionRepository.findByVerifiedTrue();
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

    /**
     * Modo Entrenamiento Adaptativo (Weak-Spot Hunter):
     * 60% preguntas de temas con menor tasa de acierto,
     * 25% preguntas falladas previamente,
     * 15% preguntas nuevas (no intentadas).
     */
    private List<Question> selectAdaptiveQuestions(int totalCount) {
        int weakCount = (int) Math.round(totalCount * 0.60);
        int failedCount = (int) Math.round(totalCount * 0.25);
        int newCount = totalCount - weakCount - failedCount;

        List<Question> selected = new ArrayList<>();
        Set<Long> pickedIds = new HashSet<>();

        // 1. Temas con menor tasa de acierto
        List<SyllabusTopic> topics = topicRepository.findAll();
        topics.sort(Comparator.comparingDouble(this::calculateTopicAccuracy));

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

        // 2. Preguntas falladas anteriormente
        List<Question> failedQuestions = questionRepository.findPreviouslyFailedQuestions();
        Collections.shuffle(failedQuestions);
        int addedFailed = 0;
        for (Question q : failedQuestions) {
            if (addedFailed < failedCount && !pickedIds.contains(q.getId())) {
                selected.add(q);
                pickedIds.add(q.getId());
                addedFailed++;
            }
        }

        // 3. Preguntas nuevas
        List<Question> newQuestions = questionRepository.findUnattemptedQuestions();
        Collections.shuffle(newQuestions);
        int addedNew = 0;
        for (Question q : newQuestions) {
            if (addedNew < newCount && !pickedIds.contains(q.getId())) {
                selected.add(q);
                pickedIds.add(q.getId());
                addedNew++;
            }
        }

        // Rellenar hasta totalCount si los subgrupos eran pequeños
        if (selected.size() < totalCount) {
            List<Question> fallback = questionRepository.findByVerifiedTrue();
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

    private double calculateTopicAccuracy(SyllabusTopic topic) {
        long total = attemptAnswerRepository.countTotalByTopicId(topic.getId());
        if (total == 0) return 0.0;
        long correct = attemptAnswerRepository.countCorrectByTopicId(topic.getId());
        return (double) correct / total;
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getRecentAttempts() {
        return attemptRepository.findTop10ByFinishedAtIsNotNullOrderByFinishedAtDesc();
    }
}
