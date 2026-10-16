package com.fhir.examapp.service;

import com.fhir.examapp.domain.ExamAttempt;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.dto.DashboardDTO;
import com.fhir.examapp.dto.RecentAttemptDTO;
import com.fhir.examapp.dto.TopicHeatmapDTO;
import com.fhir.examapp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final SyllabusTopicRepository topicRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;
    private final ExamAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSrsStateRepository srsStateRepository;
    private final SpacedRepetitionService srsService;

    public DashboardService(SyllabusTopicRepository topicRepository,
                            ExamAttemptAnswerRepository attemptAnswerRepository,
                            ExamAttemptRepository attemptRepository,
                            QuestionRepository questionRepository,
                            QuestionSrsStateRepository srsStateRepository,
                            SpacedRepetitionService srsService) {
        this.topicRepository = topicRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.srsStateRepository = srsStateRepository;
        this.srsService = srsService;
    }

    public DashboardDTO getDashboardData() {
        DashboardDTO dashboard = new DashboardDTO();

        // 1. Mapa de Calor por temas oficiales
        List<SyllabusTopic> topics = topicRepository.findAll();
        List<TopicHeatmapDTO> heatmaps = new ArrayList<>();

        double weightedScoreSum = 0.0;
        double totalWeights = 0.0;

        for (SyllabusTopic t : topics) {
            TopicHeatmapDTO h = new TopicHeatmapDTO();
            h.setTopicId(t.getId());
            h.setCode(t.getCode());
            h.setName(t.getName());
            h.setWeightPercent(t.getExamWeightPercent());

            long totalAnswered = attemptAnswerRepository.countTotalByTopicId(t.getId());
            long correctAnswered = attemptAnswerRepository.countCorrectByTopicId(t.getId());
            Double avgTime = attemptAnswerRepository.averageTimeSpentByTopicId(t.getId());

            h.setTotalAnswered(totalAnswered);
            h.setCorrectAnswered(correctAnswered);
            h.setAverageTimeSeconds(avgTime != null ? Math.round(avgTime * 10.0) / 10.0 : 0.0);

            if (totalAnswered > 0) {
                double accuracy = ((double) correctAnswered / totalAnswered) * 100.0;
                h.setAccuracyPercent(Math.round(accuracy * 10.0) / 10.0);

                // Semáforo: Rojo < 65%, Ámbar 65-79%, Verde >= 80%
                if (accuracy >= 80.0) {
                    h.setStatusColor("GREEN");
                } else if (accuracy >= 65.0) {
                    h.setStatusColor("AMBER");
                } else {
                    h.setStatusColor("RED");
                }

                weightedScoreSum += (accuracy * (t.getExamWeightPercent() / 100.0));
                totalWeights += (t.getExamWeightPercent() / 100.0);
            } else {
                h.setAccuracyPercent(0.0);
                h.setStatusColor("GRAY");
            }

            heatmaps.add(h);
        }
        dashboard.setTopicHeatmaps(heatmaps);

        // 2. Intentos recientes
        List<ExamAttempt> recentAttempts = attemptRepository.findTop10ByFinishedAtIsNotNullOrderByFinishedAtDesc();
        List<RecentAttemptDTO> recentDTOs = new ArrayList<>();
        for (ExamAttempt a : recentAttempts) {
            RecentAttemptDTO r = new RecentAttemptDTO();
            r.setId(a.getId());
            r.setMode(a.getMode());
            r.setStartedAt(a.getStartedAt());
            r.setFinishedAt(a.getFinishedAt());
            r.setScorePercent(a.getScorePercent());
            r.setTotalQuestions(a.getAnswers().size());
            int correct = (int) a.getAnswers().stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count();
            r.setCorrectQuestions(correct);
            recentDTOs.add(r);
        }
        dashboard.setRecentAttempts(recentDTOs);

        // 3. Conteo de preguntas y estado de dominio
        long totalQuestions = questionRepository.count();
        dashboard.setTotalQuestionsCount(totalQuestions);

        long pendingErrors = questionRepository.findErrorInboxQuestions().size();
        dashboard.setPendingErrorsCount(pendingErrors);

        long dueReviews = srsService.getDueCount();
        dashboard.setDueReviewsCount(dueReviews);

        // Mastered: preguntas con >= 2 aciertos consecutivos en SRS
        long masteredCount = srsStateRepository.countMasteredQuestions();
        dashboard.setMasteredQuestionsCount(masteredCount);

        // 4. Métrica de velocidad global
        Double avgGlobalTime = attemptAnswerRepository.averageTimeSpentGlobal();
        dashboard.setAverageTimePerQuestionSeconds(avgGlobalTime != null ? Math.round(avgGlobalTime * 10.0) / 10.0 : 0.0);

        // 5. Cálculo del Readiness Score (Probabilidad de Aprobado):
        // Ponderación: 
        // - 50% nota media de los últimos simulacros
        // - 30% precisión ponderada de temas
        // - 20% cobertura de preguntas dominadas sin fallos
        double simulationAvg = 0.0;
        List<ExamAttempt> sims = attemptRepository.findRecentSimulationAttempts();
        if (!sims.isEmpty()) {
            int take = Math.min(3, sims.size());
            double sum = 0.0;
            for (int i = 0; i < take; i++) {
                sum += (sims.get(i).getScorePercent() != null ? sims.get(i).getScorePercent() : 0.0);
            }
            simulationAvg = sum / take;
        }

        double topicAccuracyScore = totalWeights > 0 ? (weightedScoreSum / totalWeights) : 0.0;
        double coverageScore = totalQuestions > 0 ? ((double) masteredCount / totalQuestions) * 100.0 : 0.0;

        double readiness;
        if (!sims.isEmpty()) {
            readiness = (simulationAvg * 0.50) + (topicAccuracyScore * 0.30) + (coverageScore * 0.20);
        } else if (totalWeights > 0) {
            readiness = (topicAccuracyScore * 0.70) + (coverageScore * 0.30);
        } else {
            readiness = coverageScore * 0.50;
        }

        readiness = Math.max(0.0, Math.min(100.0, readiness));
        dashboard.setReadinessScore(Math.round(readiness * 10.0) / 10.0);

        if (readiness >= 80.0) {
            dashboard.setReadinessStatus("EXCELLENT"); // Preparado para aprobar examen HL7
        } else if (readiness >= 65.0) {
            dashboard.setReadinessStatus("ON_TRACK");
        } else if (readiness > 0.0) {
            dashboard.setReadinessStatus("NEEDS_WORK");
        } else {
            dashboard.setReadinessStatus("STARTING");
        }

        return dashboard;
    }
}
