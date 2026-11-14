package com.fhir.examapp.service;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.QuestionSrsState;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.dto.ExamOptionDTO;
import com.fhir.examapp.dto.FlashcardDTO;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.QuestionSrsStateRepository;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class SpacedRepetitionService {

    private final QuestionSrsStateRepository srsStateRepository;
    private final QuestionRepository questionRepository;
    private final SecurityContextHelper securityContextHelper;
    private CertificationContextHelper certificationContextHelper;

    public SpacedRepetitionService(QuestionSrsStateRepository srsStateRepository, QuestionRepository questionRepository) {
        this(srsStateRepository, questionRepository, null);
    }

    @Autowired
    public SpacedRepetitionService(QuestionSrsStateRepository srsStateRepository,
                                   QuestionRepository questionRepository,
                                   SecurityContextHelper securityContextHelper) {
        this.srsStateRepository = srsStateRepository;
        this.questionRepository = questionRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @Autowired
    public void setCertificationContextHelper(@Autowired(required = false) CertificationContextHelper certificationContextHelper) {
        this.certificationContextHelper = certificationContextHelper;
    }

    private Long resolveCurrentUserId() {
        return securityContextHelper != null ? securityContextHelper.getCurrentUserId() : 1L;
    }

    private Long resolveActiveCertificationId() {
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

    /**
     * Aplica el algoritmo SuperMemo-2 (SM-2) para calcular el próximo intervalo de repaso.
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

        ef = ef + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        if (ef < 1.3) {
            ef = 1.3;
        }

        state.setConsecutiveCorrect(consecutive);
        state.setEasinessFactor(Math.round(ef * 100.0) / 100.0);
        state.setIntervalDays(interval);
        state.setNextReviewDate(LocalDate.now().plusDays(interval));
        state.setUpdatedAt(User.formatNow());

        return srsStateRepository.save(state);
    }

    /**
     * Actualiza el estado SM-2 automáticamente cuando una pregunta es contestada en un examen.
     */
    public void recordAnswerResult(Question question, boolean correct) {
        recordAnswerResult(resolveCurrentUserId(), question, correct);
    }

    public void recordAnswerResult(Long userId, Question question, boolean correct) {
        Long resolvedUserId = userId != null ? userId : resolveCurrentUserId();
        QuestionSrsState state = srsStateRepository.findByUserIdAndQuestionId(resolvedUserId, question.getId())
                .orElseGet(() -> {
                    QuestionSrsState newState = new QuestionSrsState(resolvedUserId, question.getId());
                    if (question.getCertification() != null) {
                        newState.setCertification(question.getCertification());
                    }
                    return srsStateRepository.save(newState);
                });

        if (state.getCertification() == null && question.getCertification() != null) {
            state.setCertification(question.getCertification());
        }

        int rating = correct ? 4 : 1;
        applySm2(state, rating);
    }

    /**
     * Registra una valoración manual desde el modo Flashcard para el usuario autenticado.
     */
    public QuestionSrsState recordManualReview(Long questionId, int rating) {
        return recordManualReview(resolveCurrentUserId(), questionId, rating);
    }

    public QuestionSrsState recordManualReview(Long userId, Long questionId, int rating) {
        Long resolvedUserId = userId != null ? userId : resolveCurrentUserId();
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada: " + questionId));
        if (Boolean.TRUE.equals(question.getDeleted())) {
            throw new IllegalArgumentException("Pregunta eliminada: " + questionId);
        }

        QuestionSrsState state = srsStateRepository.findByUserIdAndQuestionId(resolvedUserId, question.getId())
                .orElseGet(() -> {
                    QuestionSrsState newState = new QuestionSrsState(resolvedUserId, questionId);
                    if (question.getCertification() != null) {
                        newState.setCertification(question.getCertification());
                    }
                    return srsStateRepository.save(newState);
                });

        if (state.getCertification() == null && question.getCertification() != null) {
            state.setCertification(question.getCertification());
        }

        return applySm2(state, rating);
    }

    /**
     * Obtiene las tarjetas de repaso pendientes para hoy o retrasadas para el usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<FlashcardDTO> getDueFlashcards() {
        return getDueFlashcardsForUser(resolveCurrentUserId());
    }

    @Transactional(readOnly = true)
    public List<FlashcardDTO> getDueFlashcardsForUser(Long userId) {
        return getDueFlashcardsForUser(userId, resolveActiveCertificationId());
    }

    @Transactional(readOnly = true)
    public List<FlashcardDTO> getDueFlashcardsForUser(Long userId, Long certificationId) {
        Long resolvedUserId = userId != null ? userId : resolveCurrentUserId();
        LocalDate today = LocalDate.now();

        List<Question> activeQuestions;
        if (certificationId != null) {
            activeQuestions = questionRepository.findByCertificationIdAndVerifiedTrue(certificationId);
        } else {
            activeQuestions = questionRepository.findByVerifiedTrue();
        }

        if (activeQuestions.isEmpty()) {
            return List.of();
        }

        Map<Long, QuestionSrsState> stateByQuestionId = srsStateRepository.findByUserId(resolvedUserId).stream()
                .collect(Collectors.toMap(QuestionSrsState::getQuestionId, Function.identity(), (a, b) -> a));

        List<FlashcardDTO> list = new ArrayList<>();
        for (Question q : activeQuestions) {
            QuestionSrsState state = stateByQuestionId.get(q.getId());
            if (state == null) {
                // Pregunta aún no revisada por este usuario: está pendiente para hoy
                QuestionSrsState defaultState = new QuestionSrsState(resolvedUserId, q.getId());
                if (q.getCertification() != null) {
                    defaultState.setCertification(q.getCertification());
                }
                FlashcardDTO dto = toFlashcardDTO(defaultState, q);
                if (dto != null) list.add(dto);
            } else {
                LocalDate nextReview = state.getNextReviewDate();
                if (nextReview == null || !nextReview.isAfter(today)) {
                    FlashcardDTO dto = toFlashcardDTO(state, q);
                    if (dto != null) list.add(dto);
                }
            }
        }

        Collections.shuffle(list);
        return list;
    }

    @Transactional(readOnly = true)
    public long getDueCount() {
        return getDueCountForUser(resolveCurrentUserId());
    }

    @Transactional(readOnly = true)
    public long getDueCountForUser(Long userId) {
        return getDueCountForUser(userId, resolveActiveCertificationId());
    }

    @Transactional(readOnly = true)
    public long getDueCountForUser(Long userId, Long certificationId) {
        Long resolvedUserId = userId != null ? userId : resolveCurrentUserId();
        LocalDate today = LocalDate.now();

        List<Question> activeQuestions;
        if (certificationId != null) {
            activeQuestions = questionRepository.findByCertificationIdAndVerifiedTrue(certificationId);
        } else {
            activeQuestions = questionRepository.findByVerifiedTrue();
        }

        if (activeQuestions.isEmpty()) return 0;

        Map<Long, QuestionSrsState> stateByQuestionId = srsStateRepository.findByUserId(resolvedUserId).stream()
                .collect(Collectors.toMap(QuestionSrsState::getQuestionId, Function.identity(), (a, b) -> a));

        long due = 0;
        for (Question q : activeQuestions) {
            QuestionSrsState state = stateByQuestionId.get(q.getId());
            if (state == null || state.getNextReviewDate() == null || !state.getNextReviewDate().isAfter(today)) {
                due++;
            }
        }
        return due;
    }

    public void ensureSrsState(Question question) {
        ensureSrsState(resolveCurrentUserId(), question);
    }

    public void ensureSrsState(Long userId, Question question) {
        Long resolvedUserId = userId != null ? userId : resolveCurrentUserId();
        if (question != null && question.getId() != null
                && !srsStateRepository.existsByUserIdAndQuestionId(resolvedUserId, question.getId())) {
            QuestionSrsState state = new QuestionSrsState(resolvedUserId, question.getId());
            if (question.getCertification() != null) {
                state.setCertification(question.getCertification());
            }
            srsStateRepository.save(state);
        }
    }

    private FlashcardDTO toFlashcardDTO(QuestionSrsState state, Question q) {
        if (q == null || Boolean.TRUE.equals(q.getDeleted())) return null;
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
