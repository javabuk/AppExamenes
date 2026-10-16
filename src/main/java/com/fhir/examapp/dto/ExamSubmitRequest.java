package com.fhir.examapp.dto;

import java.util.List;

public class ExamSubmitRequest {
    private Long attemptId;
    private List<ExamAnswerSubmissionDTO> answers;

    public ExamSubmitRequest() {}

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public List<ExamAnswerSubmissionDTO> getAnswers() { return answers; }
    public void setAnswers(List<ExamAnswerSubmissionDTO> answers) { this.answers = answers; }
}
