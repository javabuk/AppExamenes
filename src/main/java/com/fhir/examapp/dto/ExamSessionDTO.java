package com.fhir.examapp.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExamSessionDTO {
    private Long attemptId;
    private String mode;
    private LocalDateTime startedAt;
    private Integer totalQuestions;
    private Integer timeLimitMinutes; // p. ej. 90 min para SIMULATION, null para TUTOR
    private Long materialId; // Si la sesión está enfocada a un apunte específico
    private String materialTitle;
    private Long certificationId;
    private String certificationCode;
    private String certificationName;
    private List<ExamQuestionDTO> questions;

    public ExamSessionDTO() {}

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(Integer timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public String getMaterialTitle() { return materialTitle; }
    public void setMaterialTitle(String materialTitle) { this.materialTitle = materialTitle; }

    public Long getCertificationId() { return certificationId; }
    public void setCertificationId(Long certificationId) { this.certificationId = certificationId; }

    public String getCertificationCode() { return certificationCode; }
    public void setCertificationCode(String certificationCode) { this.certificationCode = certificationCode; }

    public String getCertificationName() { return certificationName; }
    public void setCertificationName(String certificationName) { this.certificationName = certificationName; }

    public List<ExamQuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<ExamQuestionDTO> questions) { this.questions = questions; }
}
