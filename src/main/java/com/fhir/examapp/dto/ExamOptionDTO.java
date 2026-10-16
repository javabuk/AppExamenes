package com.fhir.examapp.dto;

public class ExamOptionDTO {
    private Long id;
    private String label; // 'A', 'B', 'C', 'D'
    private String text;
    private Boolean correct; // Opcional (solo visible en Tutor o evaluación)
    private String distractorReason; // Opcional (solo visible en Tutor o evaluación)

    public ExamOptionDTO() {}

    public ExamOptionDTO(Long id, String label, String text) {
        this.id = id;
        this.label = label;
        this.text = text;
    }

    public ExamOptionDTO(Long id, String label, String text, Boolean correct, String distractorReason) {
        this.id = id;
        this.label = label;
        this.text = text;
        this.correct = correct;
        this.distractorReason = distractorReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }

    public String getDistractorReason() { return distractorReason; }
    public void setDistractorReason(String distractorReason) { this.distractorReason = distractorReason; }
}
