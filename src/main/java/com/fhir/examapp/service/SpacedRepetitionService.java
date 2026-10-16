package com.fhir.examapp.service;

import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.QuestionSrsState;
import com.fhir.examapp.dto.ExamOptionDTO;
import com.fhir.examapp.dto.FlashcardDTO;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.QuestionSrsStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SpacedRepetitionService {

    private final QuestionSrsStateRepository srsStateRepository;
    private final QuestionRepository questionRepository;

    public SpacedRepetitionService(QuestionSrsStateRepository srsStateRepository, QuestionRepository questionRepository) {
        this.srsStateRepository = srsStateRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Aplica el algoritmo SuperMemo-2 (SM-2) para calcular el próximo intervalo de repaso.
     * Rating q:
     * 5 - Respuesta perfecta
     * 4 - Respuesta correcta tras ligera duda
     * 3 - Respuesta correcta con seria dificultad
     * 2 - Respuesta incorrecta pero recordada al ver la solución
     * 1 - Respuesta incorrecta, solución familiar
     * 0 - Olvido total (blackout)
     */
    public QuestionSrsState applySm2(QuestionSrsState state, int rating) {
        int q = Math.max(0, Math.min(5, rating));
        int consecutive = state.getConsecutiveCorrect() != null ? state.getConsecutiveCorrect() : 0;
        double ef = state.getEasinessFactor() != null ? state.getEasinessFactor() : 2.5;
        int interval = state.getIntervalDays() != null ? state.getIntervalDays() : 1;

        if (q < 3) {
            consecutive = 0;
            interval = 1;
        } else {
            if (consecutive == 0) {
                interval = 1;
            } else if (consecutive == 1) {
                interval = 6;
            } else {
                interval = (int) Math.round(interval * ef);
            }
            consecutive++;
        }

        // Nueva fórmula de factor de facilidad EF
        ef = ef + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        if (ef < 1.3) {
            ef = 1.3;
        }

        state.setConsecutiveCorrect(consecutive);
        state.setEasinessFactor(Math.round(ef * 100.0) / 100.0);
        state.setIntervalDays(interval);
        state.setNextReviewDate(LocalDate.now().plusDays(interval));

        return srsStateRepository.save(state);
    }

    /**
     * Actualiza el estado SM-2 automáticamente cuando una pregunta es contestada en un examen.
     */
    public void recordAnswerResult(Question question, boolean correct) {
        QuestionSrsState state = srsStateRepository.findById(question.getId())
                .orElseGet(() -> {
                    QuestionSrsState newState = new QuestionSrsState(question.getId());
                    return srsStateRepository.save(newState);
                });

        int rating = correct ? 4 : 1;
        applySm2(state, rating);
    }

    /**
     * Registra una valoración manual desde el modo Flashcard.
     */
    public QuestionSrsState recordManualReview(Long questionId, int rating) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada: " + questionId));

        QuestionSrsState state = srsStateRepository.findById(questionId)
                .orElseGet(() -> {
                    QuestionSrsState newState = new QuestionSrsState(questionId);
                    return srsStateRepository.save(newState);
                });

        return applySm2(state, rating);
    }

    /**
     * Obtiene las tarjetas de repaso pendientes para hoy o retrasadas.
     */
    @Transactional(readOnly = true)
    public List<FlashcardDTO> getDueFlashcards() {
        String today = LocalDate.now().toString();
        List<QuestionSrsState> dueStates = srsStateRepository.findDueSrsStates(today);
        if (dueStates.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = dueStates.stream().map(QuestionSrsState::getQuestionId).collect(Collectors.toList());
        java.util.Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, java.util.function.Function.identity()));

        return dueStates.stream()
                .map(s -> toFlashcardDTO(s, questionMap.get(s.getQuestionId())))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getDueCount() {
        return srsStateRepository.countDueReviews(LocalDate.now().toString());
    }

    public void ensureSrsState(Question question) {
        if (question != null && question.getId() != null && !srsStateRepository.existsById(question.getId())) {
            QuestionSrsState state = new QuestionSrsState(question.getId());
            srsStateRepository.save(state);
        }
    }

    private FlashcardDTO toFlashcardDTO(QuestionSrsState state, Question q) {
        if (q == null) return null;
        FlashcardDTO dto = new FlashcardDTO();
        dto.setQuestionId(q.getId());
        dto.setTopicName(q.getTopic() != null ? q.getTopic().getName() : "General");
        dto.setStem(q.getStem());
        dto.setCodeSnippet(q.getCodeSnippet());
        dto.setExplanation(q.getExplanation());
        dto.setFhirSpecUrl(q.getFhirSpecUrl());
        dto.setConsecutiveCorrect(state.getConsecutiveCorrect());
        dto.setEasinessFactor(state.getEasinessFactor());
        dto.setIntervalDays(state.getIntervalDays());
        dto.setNextReviewDate(state.getNextReviewDate());

        // Encontrar opción correcta
        if (q.getOptions() != null) {
            for (QuestionOption opt : q.getOptions()) {
                if (Boolean.TRUE.equals(opt.getCorrect())) {
                    dto.setCorrectOptionLabel(opt.getOptionLabel());
                    dto.setCorrectOptionText(opt.getOptionText());
                    break;
                }
            }

            List<ExamOptionDTO> options = q.getOptions().stream()
                    .map(o -> new ExamOptionDTO(o.getId(), o.getOptionLabel(), o.getOptionText(), o.getCorrect(), o.getDistractorReason()))
                    .collect(Collectors.toList());
            dto.setOptions(options);
        }

        return dto;
    }
}
