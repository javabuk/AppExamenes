package com.fhir.examapp.service;

import com.fhir.examapp.domain.ExamAttempt;
import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.QuestionOption;
import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.dto.MaterialStatsDTO;
import com.fhir.examapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class StudyMaterialServiceTest {

    private StudyMaterialRepository studyMaterialRepository;
    private SyllabusTopicRepository syllabusTopicRepository;
    private QuestionRepository questionRepository;
    private QuestionOptionRepository questionOptionRepository;
    private SpacedRepetitionService srsService;
    private ExamAttemptRepository attemptRepository;
    private ExamAttemptAnswerRepository attemptAnswerRepository;
    private StudyMaterialService studyMaterialService;

    @BeforeEach
    void setUp() {
        studyMaterialRepository = Mockito.mock(StudyMaterialRepository.class);
        syllabusTopicRepository = Mockito.mock(SyllabusTopicRepository.class);
        questionRepository = Mockito.mock(QuestionRepository.class);
        questionOptionRepository = Mockito.mock(QuestionOptionRepository.class);
        srsService = Mockito.mock(SpacedRepetitionService.class);
        attemptRepository = Mockito.mock(ExamAttemptRepository.class);
        attemptAnswerRepository = Mockito.mock(ExamAttemptAnswerRepository.class);

        studyMaterialService = new StudyMaterialService(
                studyMaterialRepository,
                syllabusTopicRepository,
                questionRepository,
                questionOptionRepository,
                srsService,
                attemptRepository,
                attemptAnswerRepository
        );
    }

    @Test
    void testGetMaterialStats_CalculatesMetricsAndEvolution() {
        SyllabusTopic topic = new SyllabusTopic("REST_SEARCH", "Búsquedas FHIR", 25.0, "PENDING");
        topic.setId(10L);

        StudyMaterial material = new StudyMaterial(topic, "TEXT", "Guía REST", "Contenido", "REST");
        material.setId(1L);

        when(studyMaterialRepository.findById(1L)).thenReturn(Optional.of(material));
        when(questionRepository.countByMaterialIdAndVerifiedTrue(1L)).thenReturn(3L);

        // Crear 2 intentos completados
        ExamAttempt a1 = new ExamAttempt("TUTOR");
        a1.setId(101L);
        a1.setScorePercent(70.0);
        a1.setMaterial(material);
        a1.setStartedAt(java.time.LocalDateTime.of(2026, 10, 16, 10, 0));
        a1.setFinishedAt(java.time.LocalDateTime.of(2026, 10, 16, 10, 10));

        ExamAttempt a2 = new ExamAttempt("TUTOR");
        a2.setId(102L);
        a2.setScorePercent(90.0);
        a2.setMaterial(material);
        a2.setStartedAt(java.time.LocalDateTime.of(2026, 10, 17, 11, 0));
        a2.setFinishedAt(java.time.LocalDateTime.of(2026, 10, 17, 11, 8));

        when(attemptRepository.findFinishedAttemptsForMaterial(1L)).thenReturn(List.of(a1, a2));

        Question q1 = new Question();
        q1.setId(50L);
        q1.setStem("¿Qué es _include?");
        when(questionRepository.findByMaterialIdAndVerifiedTrue(1L)).thenReturn(List.of(q1));
        when(attemptAnswerRepository.findAnswersByMaterialId(1L)).thenReturn(Collections.emptyList());

        MaterialStatsDTO stats = studyMaterialService.getMaterialStats(1L);

        assertNotNull(stats);
        assertEquals(1L, stats.getMaterialId());
        assertEquals("Guía REST", stats.getMaterialTitle());
        assertEquals(2, stats.getTotalAttempts());
        assertEquals(1, stats.getPassedAttempts()); // a2 (90%) >= 80%
        assertEquals(1, stats.getFailedAttempts()); // a1 (70%) < 80%
        assertEquals(50.0, stats.getPassRatePercent());
        assertEquals(80.0, stats.getAverageScore()); // (70 + 90)/2 = 80.0
        assertEquals(90.0, stats.getBestScore());
        assertEquals(90.0, stats.getLatestScore());
        assertEquals(2, stats.getEvolution().size());
        assertEquals(70.0, stats.getEvolution().get(0).getScorePercent());
        assertFalse(stats.getEvolution().get(0).isPassed());
        assertEquals(90.0, stats.getEvolution().get(1).getScorePercent());
        assertTrue(stats.getEvolution().get(1).isPassed());
        assertEquals(1, stats.getQuestionPerformance().size());
        assertEquals("SIN_INTENTOS", stats.getQuestionPerformance().get(0).getStatus());
    }
}
