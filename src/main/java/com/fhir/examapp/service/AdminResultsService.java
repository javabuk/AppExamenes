package com.fhir.examapp.service;

import com.fhir.examapp.domain.*;
import com.fhir.examapp.dto.DashboardDTO;
import com.fhir.examapp.dto.ExamResultDTO;
import com.fhir.examapp.dto.MaterialEvolutionPointDTO;
import com.fhir.examapp.dto.TopicHeatmapDTO;
import com.fhir.examapp.dto.admin.*;
import com.fhir.examapp.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminResultsService {

    private final UserRepository userRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final SyllabusTopicRepository topicRepository;
    private final QuestionSrsStateRepository srsStateRepository;
    private final DashboardService dashboardService;
    private final ExamEngineService examEngineService;

    public AdminResultsService(UserRepository userRepository,
                               ExamAttemptRepository attemptRepository,
                               ExamAttemptAnswerRepository attemptAnswerRepository,
                               QuestionRepository questionRepository,
                               StudyMaterialRepository studyMaterialRepository,
                               SyllabusTopicRepository topicRepository,
                               QuestionSrsStateRepository srsStateRepository,
                               DashboardService dashboardService,
                               ExamEngineService examEngineService) {
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.questionRepository = questionRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.topicRepository = topicRepository;
        this.srsStateRepository = srsStateRepository;
        this.dashboardService = dashboardService;
        this.examEngineService = examEngineService;
    }

    public List<AdminUserKpiDTO> getAllUsersKpis() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(this::buildUserKpi)
                .collect(Collectors.toList());
    }

    public AdminUserDetailDTO getUserDetail(Long userId, String mode, String fromDate, String toDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        AdminUserDetailDTO detail = new AdminUserDetailDTO();
        detail.setUser(AdminUserDTO.fromEntity(user));

        DashboardDTO dashboard = dashboardService.getDashboardDataForUser(userId);
        detail.setDashboard(dashboard);
        detail.setKpi(buildUserKpiWithDashboard(user, dashboard));

        List<ExamAttempt> allUserAttempts = attemptRepository.findFinishedByUserIdOrderByFinishedAtDesc(userId);
        List<ExamAttempt> filteredAttempts = filterAttempts(allUserAttempts, null, mode, fromDate, toDate);

        // 1. Historial de intentos (más reciente primero)
        List<Map<String, Object>> attemptItems = new ArrayList<>();
        for (ExamAttempt a : filteredAttempts) {
            attemptItems.add(mapAttemptSummary(a));
        }
        detail.setAttempts(attemptItems);

        // 2. Curva de evolución cronológica (más antiguo primero)
        List<ExamAttempt> chronological = new ArrayList<>(filteredAttempts);
        Collections.reverse(chronological);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", new Locale("es", "ES"));
        List<MaterialEvolutionPointDTO> evolution = new ArrayList<>();
        int idx = 1;
        for (ExamAttempt a : chronological) {
            double score = a.getScorePercent() != null ? Math.round(a.getScorePercent() * 10.0) / 10.0 : 0.0;
            boolean passed = score >= 80.0;
            int totalQ = a.getAnswers() != null ? a.getAnswers().size() : 0;
            int correctQ = a.getAnswers() != null
                    ? (int) a.getAnswers().stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count()
                    : 0;
            int durationSec = computeAttemptDurationSeconds(a);
            String label = a.getStartedAt() != null ? a.getStartedAt().format(fmt) : "#" + idx;

            evolution.add(new MaterialEvolutionPointDTO(
                    a.getId(),
                    idx++,
                    a.getStartedAt() != null ? a.getStartedAt().toString() : "",
                    a.getFinishedAt() != null ? a.getFinishedAt().toString() : "",
                    label,
                    a.getMode(),
                    score,
                    correctQ,
                    totalQ,
                    durationSec,
                    passed
            ));
        }
        detail.setEvolution(evolution);

        // 3. Preguntas más falladas por este usuario (sobre los intentos filtrados)
        List<ExamAttemptAnswer> userAnswers = filteredAttempts.stream()
                .filter(a -> a.getAnswers() != null)
                .flatMap(a -> a.getAnswers().stream())
                .collect(Collectors.toList());
        detail.setMostFailedQuestions(computeMostFailedQuestions(userAnswers, 15));

        // 4. Resumen de actividad SRS del usuario
        List<QuestionSrsState> srsStates = srsStateRepository.findByUserId(userId);
        LocalDate today = LocalDate.now();
        long mastered = srsStates.stream().filter(s -> s.getConsecutiveCorrect() != null && s.getConsecutiveCorrect() >= 2).count();
        long dueNow = srsStates.stream().filter(s -> s.getNextReviewDate() != null && !s.getNextReviewDate().isAfter(today)).count();
        long learning = srsStates.stream().filter(s -> s.getConsecutiveCorrect() == null || s.getConsecutiveCorrect() < 2).count();
        double avgEf = srsStates.stream()
                .filter(s -> s.getEasinessFactor() != null)
                .mapToDouble(QuestionSrsState::getEasinessFactor)
                .average()
                .orElse(2.5);
        int totalReps = srsStates.stream()
                .filter(s -> s.getConsecutiveCorrect() != null)
                .mapToInt(QuestionSrsState::getConsecutiveCorrect)
                .sum();

        Map<String, Object> srsActivity = new LinkedHashMap<>();
        srsActivity.put("totalTracked", srsStates.size());
        srsActivity.put("masteredCount", mastered);
        srsActivity.put("dueNowCount", dueNow);
        srsActivity.put("learningCount", learning);
        srsActivity.put("averageEasinessFactor", Math.round(avgEf * 100.0) / 100.0);
        srsActivity.put("totalRepetitionSteps", totalReps);
        detail.setSrsActivity(srsActivity);

        return detail;
    }

    public ExamResultDTO getUserAttemptReview(Long userId, Long attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Intento de examen no encontrado: " + attemptId));
        if (attempt.getUser() == null || !attempt.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("El intento indicado no pertenece al usuario especificado.");
        }
        return examEngineService.getAttemptReview(attemptId);
    }

    public AdminAggregateResultsDTO getAggregateResults(String mode, String fromDate, String toDate, Long userId) {
        AdminAggregateResultsDTO dto = new AdminAggregateResultsDTO();

        List<User> allUsers = userRepository.findAll();
        dto.setTotalUsers(allUsers.size());
        dto.setActiveUsers(allUsers.stream().filter(u -> Boolean.TRUE.equals(u.getActive())).count());
        dto.setTotalQuestionsInBank(questionRepository.countActiveVerified());
        dto.setTotalMaterialsCount(studyMaterialRepository.findAllActive().size());

        List<ExamAttempt> allFinished = attemptRepository.findAllFinishedOrderByFinishedAtDesc();
        List<ExamAttempt> filteredAttempts = filterAttempts(allFinished, userId, mode, fromDate, toDate);

        dto.setTotalCompletedExams(filteredAttempts.size());
        int passed = 0;
        double sumScore = 0.0;
        for (ExamAttempt a : filteredAttempts) {
            double score = a.getScorePercent() != null ? a.getScorePercent() : 0.0;
            sumScore += score;
            if (score >= 80.0) {
                passed++;
            }
        }
        dto.setTotalPassedExams(passed);
        if (!filteredAttempts.isEmpty()) {
            dto.setGlobalAverageScore(Math.round((sumScore / filteredAttempts.size()) * 10.0) / 10.0);
            dto.setGlobalPassRatePercent(Math.round(((double) passed / filteredAttempts.size()) * 1000.0) / 10.0);
        }

        // Respuestas de los intentos filtrados
        List<ExamAttemptAnswer> filteredAnswers = filteredAttempts.stream()
                .filter(a -> a.getAnswers() != null)
                .flatMap(a -> a.getAnswers().stream())
                .collect(Collectors.toList());

        // Media global por cada una de las 6 áreas del syllabus
        List<SyllabusTopic> topics = topicRepository.findAll();
        List<TopicHeatmapDTO> topicAverages = new ArrayList<>();
        for (SyllabusTopic t : topics) {
            List<ExamAttemptAnswer> topicAnswers = filteredAnswers.stream()
                    .filter(ans -> ans.getQuestion() != null
                            && ans.getQuestion().getTopic() != null
                            && t.getId().equals(ans.getQuestion().getTopic().getId()))
                    .collect(Collectors.toList());

            long totalAns = topicAnswers.size();
            long correctAns = topicAnswers.stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count();
            double avgTime = topicAnswers.stream()
                    .filter(ans -> ans.getTimeSpentSeconds() != null)
                    .mapToInt(ExamAttemptAnswer::getTimeSpentSeconds)
                    .average()
                    .orElse(0.0);

            TopicHeatmapDTO h = new TopicHeatmapDTO();
            h.setTopicId(t.getId());
            h.setCode(t.getCode());
            h.setName(t.getName());
            h.setWeightPercent(t.getExamWeightPercent());
            h.setTotalAnswered(totalAns);
            h.setCorrectAnswered(correctAns);
            h.setAverageTimeSeconds(Math.round(avgTime * 10.0) / 10.0);

            if (totalAns > 0) {
                double acc = Math.round(((double) correctAns / totalAns) * 1000.0) / 10.0;
                h.setAccuracyPercent(acc);
                if (acc >= 80.0) h.setStatusColor("GREEN");
                else if (acc >= 65.0) h.setStatusColor("AMBER");
                else h.setStatusColor("RED");
            } else {
                h.setAccuracyPercent(0.0);
                h.setStatusColor("GRAY");
            }
            topicAverages.add(h);
        }
        dto.setTopicAverages(topicAverages);

        // Preguntas con mayor tasa de fallo global
        dto.setMostFailedQuestions(computeMostFailedQuestions(filteredAnswers, 15));

        // Ranking / comparativa de usuarios
        List<AdminUserKpiDTO> ranking = allUsers.stream()
                .map(this::buildUserKpi)
                .sorted(Comparator.comparingDouble(AdminUserKpiDTO::getReadinessScore).reversed()
                        .thenComparing(Comparator.comparingDouble(AdminUserKpiDTO::getAverageScore).reversed())
                        .thenComparing(Comparator.comparingInt(AdminUserKpiDTO::getTotalExams).reversed()))
                .collect(Collectors.toList());
        dto.setUserRanking(ranking);

        return dto;
    }

    public String exportResultsCsv(Long userId, String mode, String fromDate, String toDate) {
        List<ExamAttempt> allFinished = attemptRepository.findAllFinishedOrderByFinishedAtDesc();
        List<ExamAttempt> filteredAttempts = filterAttempts(allFinished, userId, mode, fromDate, toDate);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM para apertura directa en Excel
        sb.append('\uFEFF');
        sb.append("AttemptId,UserId,Username,DisplayName,Email,Mode,StartedAt,FinishedAt,DurationSeconds,TotalQuestions,CorrectAnswers,ScorePercent,Passed,Topic,Material\n");

        for (ExamAttempt a : filteredAttempts) {
            User u = a.getUser();
            int totalQ = a.getAnswers() != null ? a.getAnswers().size() : 0;
            int correctQ = a.getAnswers() != null
                    ? (int) a.getAnswers().stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count()
                    : 0;
            double score = a.getScorePercent() != null ? Math.round(a.getScorePercent() * 10.0) / 10.0 : 0.0;
            boolean passed = score >= 80.0;
            int durationSec = computeAttemptDurationSeconds(a);
            String topicName = resolveAttemptTopicName(a);
            String materialTitle = a.getMaterial() != null ? a.getMaterial().getTitle() : "";

            sb.append(a.getId()).append(',')
              .append(u != null ? u.getId() : "").append(',')
              .append(escapeCsv(u != null ? u.getUsername() : "")).append(',')
              .append(escapeCsv(u != null ? u.getDisplayName() : "")).append(',')
              .append(escapeCsv(u != null ? u.getEmail() : "")).append(',')
              .append(escapeCsv(a.getMode())).append(',')
              .append(escapeCsv(a.getStartedAt() != null ? a.getStartedAt().toString() : "")).append(',')
              .append(escapeCsv(a.getFinishedAt() != null ? a.getFinishedAt().toString() : "")).append(',')
              .append(durationSec).append(',')
              .append(totalQ).append(',')
              .append(correctQ).append(',')
              .append(score).append(',')
              .append(passed ? "SI" : "NO").append(',')
              .append(escapeCsv(topicName)).append(',')
              .append(escapeCsv(materialTitle))
              .append('\n');
        }
        return sb.toString();
    }

    private AdminUserKpiDTO buildUserKpi(User user) {
        DashboardDTO dashboard = dashboardService.getDashboardDataForUser(user.getId());
        return buildUserKpiWithDashboard(user, dashboard);
    }

    private AdminUserKpiDTO buildUserKpiWithDashboard(User user, DashboardDTO dashboard) {
        AdminUserKpiDTO kpi = new AdminUserKpiDTO();
        kpi.setUserId(user.getId());
        kpi.setUsername(user.getUsername());
        kpi.setDisplayName(user.getDisplayName());
        kpi.setEmail(user.getEmail());
        kpi.setRole(user.getRole() != null ? user.getRole().name() : "USER");
        kpi.setActive(user.getActive());
        kpi.setLastLoginAt(user.getLastLoginAt());
        kpi.setReadinessScore(dashboard.getReadinessScore());
        kpi.setReadinessStatus(dashboard.getReadinessStatus());
        kpi.setMasteredQuestionsCount(dashboard.getMasteredQuestionsCount());
        kpi.setDueReviewsCount(dashboard.getDueReviewsCount());

        List<ExamAttempt> attempts = attemptRepository.findFinishedByUserIdOrderByFinishedAtDesc(user.getId());
        kpi.setTotalExams(attempts.size());
        if (!attempts.isEmpty()) {
            int passed = 0;
            double sum = 0.0;
            for (ExamAttempt a : attempts) {
                double s = a.getScorePercent() != null ? a.getScorePercent() : 0.0;
                sum += s;
                if (s >= 80.0) passed++;
            }
            kpi.setPassedExams(passed);
            kpi.setPassRatePercent(Math.round(((double) passed / attempts.size()) * 1000.0) / 10.0);
            kpi.setAverageScore(Math.round((sum / attempts.size()) * 10.0) / 10.0);

            ExamAttempt latest = attempts.get(0);
            kpi.setLastAttemptAt(latest.getFinishedAt() != null ? latest.getFinishedAt().toString() : null);
            kpi.setLastAttemptScore(latest.getScorePercent() != null ? Math.round(latest.getScorePercent() * 10.0) / 10.0 : 0.0);
            kpi.setLastAttemptMode(latest.getMode());
        }
        return kpi;
    }

    private List<ExamAttempt> filterAttempts(List<ExamAttempt> source, Long userId, String mode, String fromDate, String toDate) {
        LocalDate from = parseLocalDate(fromDate);
        LocalDate to = parseLocalDate(toDate);
        String normalizedMode = (mode != null && !mode.isBlank()) ? mode.trim().toUpperCase() : null;

        return source.stream()
                .filter(a -> {
                    if (userId != null) {
                        if (a.getUser() == null || !userId.equals(a.getUser().getId())) return false;
                    }
                    if (normalizedMode != null && !normalizedMode.equalsIgnoreCase(a.getMode())) {
                        return false;
                    }
                    LocalDateTime refTime = a.getFinishedAt() != null ? a.getFinishedAt() : a.getStartedAt();
                    if (refTime != null) {
                        LocalDate attemptDate = refTime.toLocalDate();
                        if (from != null && attemptDate.isBefore(from)) return false;
                        if (to != null && attemptDate.isAfter(to)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim().substring(0, Math.min(10, dateStr.trim().length())));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> mapAttemptSummary(ExamAttempt a) {
        double score = a.getScorePercent() != null ? Math.round(a.getScorePercent() * 10.0) / 10.0 : 0.0;
        int totalQ = a.getAnswers() != null ? a.getAnswers().size() : 0;
        int correctQ = a.getAnswers() != null
                ? (int) a.getAnswers().stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count()
                : 0;
        int durationSec = computeAttemptDurationSeconds(a);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", a.getId());
        item.put("mode", a.getMode());
        item.put("startedAt", a.getStartedAt() != null ? a.getStartedAt().toString() : null);
        item.put("finishedAt", a.getFinishedAt() != null ? a.getFinishedAt().toString() : null);
        item.put("durationSeconds", durationSec);
        item.put("scorePercent", score);
        item.put("passed", score >= 80.0);
        item.put("totalQuestions", totalQ);
        item.put("correctQuestions", correctQ);
        item.put("topicName", resolveAttemptTopicName(a));
        item.put("materialId", a.getMaterial() != null ? a.getMaterial().getId() : null);
        item.put("materialTitle", a.getMaterial() != null ? a.getMaterial().getTitle() : null);
        return item;
    }

    private String resolveAttemptTopicName(ExamAttempt a) {
        if (a.getMaterial() != null && a.getMaterial().getTopic() != null) {
            return a.getMaterial().getTopic().getName();
        }
        if (a.getAnswers() != null && !a.getAnswers().isEmpty()) {
            Set<String> topicNames = a.getAnswers().stream()
                    .filter(ans -> ans.getQuestion() != null && ans.getQuestion().getTopic() != null)
                    .map(ans -> ans.getQuestion().getTopic().getName())
                    .collect(Collectors.toSet());
            if (topicNames.size() == 1) {
                return topicNames.iterator().next();
            }
        }
        return "Global";
    }

    private int computeAttemptDurationSeconds(ExamAttempt a) {
        int durationSec = 0;
        if (a.getAnswers() != null) {
            for (ExamAttemptAnswer ans : a.getAnswers()) {
                if (ans.getTimeSpentSeconds() != null) {
                    durationSec += ans.getTimeSpentSeconds();
                }
            }
        }
        if (durationSec == 0 && a.getStartedAt() != null && a.getFinishedAt() != null) {
            try {
                durationSec = (int) Math.max(1, Duration.between(a.getStartedAt(), a.getFinishedAt()).getSeconds());
            } catch (Exception ignored) {
            }
        }
        return durationSec;
    }

    private List<AdminFailedQuestionDTO> computeMostFailedQuestions(List<ExamAttemptAnswer> answers, int limit) {
        Map<Long, List<ExamAttemptAnswer>> byQuestion = answers.stream()
                .filter(ans -> ans.getQuestion() != null && ans.getQuestion().getId() != null)
                .collect(Collectors.groupingBy(ans -> ans.getQuestion().getId()));

        List<AdminFailedQuestionDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<ExamAttemptAnswer>> entry : byQuestion.entrySet()) {
            List<ExamAttemptAnswer> qAnswers = entry.getValue();
            int total = qAnswers.size();
            int correct = (int) qAnswers.stream().filter(ans -> Boolean.TRUE.equals(ans.getCorrect())).count();
            int failed = total - correct;
            if (failed <= 0) continue;

            Question q = qAnswers.get(0).getQuestion();
            String topicCode = q.getTopic() != null ? q.getTopic().getCode() : "GENERAL";
            String topicName = q.getTopic() != null ? q.getTopic().getName() : "General";
            double failRate = Math.round(((double) failed / total) * 1000.0) / 10.0;

            result.add(new AdminFailedQuestionDTO(
                    q.getId(),
                    q.getStem(),
                    topicCode,
                    topicName,
                    total,
                    failed,
                    correct,
                    failRate
            ));
        }

        result.sort(Comparator.comparingInt(AdminFailedQuestionDTO::getFailedCount).reversed()
                .thenComparing(Comparator.comparingDouble(AdminFailedQuestionDTO::getFailureRatePercent).reversed()));

        return result.size() > limit ? result.subList(0, limit) : result;
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        String cleaned = val.replace("\r", " ").replace("\n", " ");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains(";")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }
}
