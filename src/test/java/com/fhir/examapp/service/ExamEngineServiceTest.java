package com.fhir.examapp.service;

import com.fhir.examapp.domain.*;
import com.fhir.examapp.dto.ExamAnswerSubmissionDTO;
import com.fhir.examapp.dto.ExamResultDTO;
import com.fhir.examapp.dto.ExamSessionDTO;
import com.fhir.examapp.dto.ExamStartRequest;
import com.fhir.examapp.dto.ExamSubmitRequest;
import com.fhir.examapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ExamEngineServiceTest {

    private ExamAttemptRepository attemptRepository;
    private ExamAttemptAnswerRepository attemptAnswerRepository;
    private QuestionRepository questionRepository;
    private SyllabusTopicRepository topicRepository;
    private SpacedRepetitionService srsService;
    private StudyMaterialRepository materialRepository;
    private ExamEngineService examEngineService;

    @BeforeEach
    void setUp() {
        attemptRepository = Mockito.mock(ExamAttemptRepository.class);
        attemptAnswerRepository = Mockito.mock(ExamAttemptAnswerRepository.class);
        questionRepository = Mockito.mock(QuestionRepository.class);
        topicRepository = Mockito.mock(SyllabusTopicRepository.class);
        srsService = Mockito.mock(SpacedRepetitionService.class);
        materialRepository = Mockito.mock(StudyMaterialRepository.class);

        examEngineService = new ExamEngineService(
                attemptRepository,
                attemptAnswerRepository,
                questionRepository,
                topicRepository,
                srsService,
                materialRepository
        );

        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(i -> {
            ExamAttempt a = i.getArgument(0);
            if (a.getId() == null) a.setId(100L);
            return a;
        });
    }

    @Test
    void testStartExam_SimulationMode() {
        SyllabusTopic topic = new SyllabusTopic("REST_SEARCH", "REST", 25.0, "PENDING");
        topic.setId(1L);

        Question q = new Question();
        q.setId(1L);
        q.setTopic(topic);
        q.setStem("¿Qué es FHIR?");
        q.addOption(new QuestionOption("A", "Fast Healthcare Interoperability Resources", true, "Correcto"));
        q.addOption(new QuestionOption("B", "Falso", false, "Incorrecto"));

        when(topicRepository.findAll()).thenReturn(List.of(topic));
        when(questionRepository.findByTopicIdAndVerifiedTrue(1L)).thenReturn(List.of(q));
        when(questionRepository.findByVerifiedTrue()).thenReturn(List.of(q));

        ExamStartRequest req = new ExamStartRequest("SIMULATION", 1, null);
        ExamSessionDTO session = examEngineService.startExam(req);

        assertNotNull(session);
        assertEquals(100L, session.getAttemptId());
        assertEquals("SIMULATION", session.getMode());
        assertEquals(1, session.getQuestions().size());
        assertEquals("¿Qué es FHIR?", session.getQuestions().get(0).getStem());
    }

    @Test
    void testSubmitExam_EvaluatesScoreAndPassThreshold() {
        ExamAttempt attempt = new ExamAttempt("SIMULATION");
        attempt.setId(100L);

        Question q = new Question();
        q.setId(1L);
        q.addOption(new QuestionOption("A", "Opción A", true, "Correcta"));
        q.addOption(new QuestionOption("B", "Opción B", false, "Incorrecta"));

        when(attemptRepository.findById(100L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q));

        ExamSubmitRequest req = new ExamSubmitRequest();
        req.setAttemptId(100L);
        req.setAnswers(List.of(
                new ExamAnswerSubmissionDTO(1L, "A", 12, false)
        ));

        ExamResultDTO result = examEngineService.submitExam(req);

        assertNotNull(result);
        assertEquals(100.0, result.getScorePercent());
        assertEquals(1, result.getCorrectCount());
        assertTrue(result.getPassed());
        assertEquals(1, result.getReviews().size());
        assertTrue(result.getReviews().get(0).getCorrect());
    }
}
