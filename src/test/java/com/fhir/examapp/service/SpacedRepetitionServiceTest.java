package com.fhir.examapp.service;

import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionSrsState;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.QuestionSrsStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SpacedRepetitionServiceTest {

    private QuestionSrsStateRepository srsStateRepository;
    private QuestionRepository questionRepository;
    private SpacedRepetitionService srsService;

    @BeforeEach
    void setUp() {
        srsStateRepository = Mockito.mock(QuestionSrsStateRepository.class);
        questionRepository = Mockito.mock(QuestionRepository.class);
        srsService = new SpacedRepetitionService(srsStateRepository, questionRepository);

        when(srsStateRepository.save(any(QuestionSrsState.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void testApplySm2_PerfectRatingAdvancesInterval() {
        Question q = new Question();
        q.setId(10L);
        QuestionSrsState state = new QuestionSrsState(q);
        state.setConsecutiveCorrect(0);
        state.setEasinessFactor(2.5);
        state.setIntervalDays(1);

        // Calificación 5 (Perfecto)
        QuestionSrsState updated = srsService.applySm2(state, 5);

        assertEquals(1, updated.getConsecutiveCorrect());
        assertEquals(1, updated.getIntervalDays());
        assertTrue(updated.getEasinessFactor() >= 2.5);
        assertEquals(LocalDate.now().plusDays(1), updated.getNextReviewDate());

        // Segunda respuesta perfecta consecutiva -> intervalo 6 días
        QuestionSrsState updated2 = srsService.applySm2(updated, 5);
        assertEquals(2, updated2.getConsecutiveCorrect());
        assertEquals(6, updated2.getIntervalDays());
        assertEquals(LocalDate.now().plusDays(6), updated2.getNextReviewDate());
    }

    @Test
    void testApplySm2_FailureResetsStreak() {
        Question q = new Question();
        q.setId(20L);
        QuestionSrsState state = new QuestionSrsState(q);
        state.setConsecutiveCorrect(4);
        state.setEasinessFactor(2.6);
        state.setIntervalDays(15);

        // Calificación 1 (Error) -> resetea streak e intervalo
        QuestionSrsState updated = srsService.applySm2(state, 1);

        assertEquals(0, updated.getConsecutiveCorrect());
        assertEquals(1, updated.getIntervalDays());
        assertTrue(updated.getEasinessFactor() < 2.6);
        assertEquals(LocalDate.now().plusDays(1), updated.getNextReviewDate());
    }
}
