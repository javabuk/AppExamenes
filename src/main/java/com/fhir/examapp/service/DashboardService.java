package com.fhir.examapp.service;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.CertificationArea;
import com.fhir.examapp.domain.ExamAttempt;
import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.dto.DashboardDTO;
import com.fhir.examapp.dto.RecentAttemptDTO;
import com.fhir.examapp.dto.TopicHeatmapDTO;
import com.fhir.examapp.repository.*;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final SecurityContextHelper securityContextHelper;
    private CertificationContextHelper certificationContextHelper;
    private CertificationRepository certificationRepository;
    private CertificationAreaRepository certificationAreaRepository;

    @Autowired
    public DashboardService(SyllabusTopicRepository topicRepository,
                            ExamAttemptAnswerRepository attemptAnswerRepository,
                            ExamAttemptRepository attemptRepository,
                            QuestionRepository questionRepository,
                            QuestionSrsStateRepository srsStateRepository,
                            SpacedRepetitionService srsService,
                            SecurityContextHelper securityContextHelper) {
        this.topicRepository = topicRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.srsStateRepository = srsStateRepository;
        this.srsService = srsService;
        this.securityContextHelper = securityContextHelper;
    }

    @Autowired
    public void setCertificationComponents(@Autowired(required = false) CertificationContextHelper certificationContextHelper,
                                          @Autowired(required = false) CertificationRepository certificationRepository,
                                          @Autowired(required = false) CertificationAreaRepository certificationAreaRepository) {
        this.certificationContextHelper = certificationContextHelper;
        this.certificationRepository = certificationRepository;
        this.certificationAreaRepository = certificationAreaRepository;
    }

    private Certification resolveActiveCertification(Long explicitCertId) {
        if (certificationContextHelper != null) {
            return certificationContextHelper.resolveActiveCertification(explicitCertId);
        }
        if (certificationRepository != null) {
            if (explicitCertId != null) {
                return certificationRepository.findById(explicitCertId).orElse(null);
            }
            return certificationRepository.findById(1L).orElse(null);
        }
        return null;
    }

    public DashboardDTO getDashboardData() {
        Long userId = securityContextHelper != null ? securityContextHelper.getCurrentUserId() : 1L;
        return getDashboardDataForUser(userId, null);
    }

    public DashboardDTO getDashboardData(Long explicitCertId) {
        Long userId = securityContextHelper != null ? securityContextHelper.getCurrentUserId() : 1L;
        return getDashboardDataForUser(userId, explicitCertId);
    }

    public DashboardDTO getDashboardDataForUser(Long userId) {
        return getDashboardDataForUser(userId, null);
    }

    public DashboardDTO getDashboardDataForUser(Long userId, Long explicitCertId) {
        DashboardDTO dashboard = new DashboardDTO();
        Certification cert = resolveActiveCertification(explicitCertId);
        Long certId = cert != null ? cert.getId() : null;

        if (cert != null) {
            dashboard.setCertificationId(cert.getId());
            dashboard.setCertificationCode(cert.getCode());
            dashboard.setCertificationName(cert.getName());
            dashboard.setPassingScorePercent(cert.getPassingScorePercent());
        }

        // 1. Mapa de Calor por temas / áreas para este usuario y certificación
        List<TopicHeatmapDTO> heatmaps = new ArrayList<>();
        double weightedScoreSum = 0.0;
        double totalWeights = 0.0;

        if (certId != null && certificationAreaRepository != null) {
            List<CertificationArea> areas = certificationAreaRepository.findByCertificationIdAndActiveTrueOrderBySortOrderAsc(certId);
            for (CertificationArea a : areas) {
                TopicHeatmapDTO h = new TopicHeatmapDTO();
                h.setTopicId(a.getId());
                h.setCode(a.getCode());
                h.setName(a.getName());
                h.setWeightPercent(a.getWeightPercent());

                long totalAnswered = attemptAnswerRepository.countTotalByUserIdAndTopicId(userId, a.getId());
                long correctAnswered = attemptAnswerRepository.countCorrectByUserIdAndTopicId(userId, a.getId());
                Double avgTime = attemptAnswerRepository.averageTimeSpentByUserIdAndTopicId(userId, a.getId());

                h.setTotalAnswered(totalAnswered);
                h.setCorrectAnswered(correctAnswered);
                h.setAverageTimeSeconds(avgTime != null ? Math.round(avgTime * 10.0) / 10.0 : 0.0);

                if (totalAnswered > 0) {
                    double accuracy = ((double) correctAnswered / totalAnswered) * 100.0;
                    h.setAccuracyPercent(Math.round(accuracy * 10.0) / 10.0);

                    if (accuracy >= 80.0) {
                        h.setStatusColor("GREEN");
                    } else if (accuracy >= 65.0) {
                        h.setStatusColor("AMBER");
                    } else {
                        h.setStatusColor("RED");
                    }

                    weightedScoreSum += (accuracy * (a.getWeightPercent() / 100.0));
                    totalWeights += (a.getWeightPercent() / 100.0);
                } else {
                    h.setAccuracyPercent(0.0);
                    h.setStatusColor("GRAY");
                }

                heatmaps.add(h);
            }
        } else {
            List<SyllabusTopic> topics = topicRepository.findAll();
            for (SyllabusTopic t : topics) {
                TopicHeatmapDTO h = new TopicHeatmapDTO();
                h.setTopicId(t.getId());
                h.setCode(t.getCode());
                h.setName(t.getName());
                h.setWeightPercent(t.getExamWeightPercent());

                long totalAnswered = attemptAnswerRepository.countTotalByUserIdAndTopicId(userId, t.getId());
                long correctAnswered = attemptAnswerRepository.countCorrectByUserIdAndTopicId(userId, t.getId());
                Double avgTime = attemptAnswerRepository.averageTimeSpentByUserIdAndTopicId(userId, t.getId());

                h.setTotalAnswered(totalAnswered);
                h.setCorrectAnswered(correctAnswered);
                h.setAverageTimeSeconds(avgTime != null ? Math.round(avgTime * 10.0) / 10.0 : 0.0);

                if (totalAnswered > 0) {
                    double accuracy = ((double) correctAnswered / totalAnswered) * 100.0;
                    h.setAccuracyPercent(Math.round(accuracy * 10.0) / 10.0);

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
        }
        dashboard.setTopicHeatmaps(heatmaps);

        // 2. Intentos recientes del usuario
        List<ExamAttempt> recentAttempts;
        if (certId != null) {
            recentAttempts = attemptRepository.findTop10ByUser_IdAndCertification_IdAndFinishedAtIsNotNullOrderByFinishedAtDesc(userId, certId);
        } else {
            recentAttempts = attemptRepository.findTop10ByUser_IdAndFinishedAtIsNotNullOrderByFinishedAtDesc(userId);
        }

        List<RecentAttemptDTO> recentDTOs = new ArrayList<>();
        for (ExamAttempt a : recentAttempts) {
            RecentAttemptDTO r = new RecentAttemptDTO();
            r.setId(a.getId());
            r.setMode(a.getMode());
            r.setStartedAt(a.getStartedAt());
            r.setFinishedAt(a.getFinishedAt());
            r.setScorePercent(a.getScorePercent());
            r.setTotalQuestions(a.getAnswers() != null ? a.getAnswers().size() : 0);
            int correct = a.getAnswers() != null
                    ? (int) a.getAnswers().stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count()
                    : 0;
            r.setCorrectQuestions(correct);
            recentDTOs.add(r);
        }
        dashboard.setRecentAttempts(recentDTOs);

        // 3. Conteo de preguntas y estado de dominio del usuario
        long totalQuestions = (certId != null)
                ? questionRepository.countActiveVerifiedByCertificationId(certId)
                : questionRepository.countActiveVerified();
        dashboard.setTotalQuestionsCount(totalQuestions);

        long pendingErrors = (certId != null)
                ? questionRepository.findErrorInboxQuestionsByUserIdAndCertificationId(userId, certId).size()
                : questionRepository.findErrorInboxQuestionsByUserId(userId).size();
        dashboard.setPendingErrorsCount(pendingErrors);

        long dueReviews = srsService.getDueCountForUser(userId, certId);
        dashboard.setDueReviewsCount(dueReviews);

        // Mastered: preguntas con >= 2 aciertos consecutivos en SRS de este usuario
        long masteredCount = (certId != null)
                ? srsStateRepository.countMasteredQuestionsByUserIdAndCertificationId(userId, certId)
                : srsStateRepository.countMasteredQuestionsByUserId(userId);
        dashboard.setMasteredQuestionsCount(masteredCount);

        // 4. Métrica de velocidad global del usuario
        Double avgGlobalTime = attemptAnswerRepository.averageTimeSpentGlobalByUserId(userId);
        dashboard.setAverageTimePerQuestionSeconds(avgGlobalTime != null ? Math.round(avgGlobalTime * 10.0) / 10.0 : 0.0);

        // 5. Cálculo del Readiness Score (Probabilidad de Aprobado) del usuario:
        double simulationAvg = 0.0;
        List<ExamAttempt> sims = (certId != null)
                ? attemptRepository.findRecentSimulationAttemptsByUserIdAndCertificationId(userId, certId)
                : attemptRepository.findRecentSimulationAttemptsByUserId(userId);

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

        double passingCutoff = cert != null ? cert.getPassingScorePercent() : 70.0;
        if (readiness >= passingCutoff + 5.0) {
            dashboard.setReadinessStatus("EXCELLENT");
        } else if (readiness >= passingCutoff) {
            dashboard.setReadinessStatus("ON_TRACK");
        } else if (readiness > 0.0) {
            dashboard.setReadinessStatus("NEEDS_WORK");
        } else {
            dashboard.setReadinessStatus("STARTING");
        }

        return dashboard;
    }
}
