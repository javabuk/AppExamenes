package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "question_options")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private Question question;

    @Column(name = "option_label", nullable = false)
    private String optionLabel; // 'A', 'B', 'C', 'D'

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    @Column(name = "distractor_reason", columnDefinition = "TEXT")
    private String distractorReason;

    public QuestionOption() {}

    public QuestionOption(String optionLabel, String optionText, Boolean correct, String distractorReason) {
        this.optionLabel = optionLabel;
        this.optionText = optionText;
        this.correct = correct;
        this.distractorReason = distractorReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }

    public String getDistractorReason() { return distractorReason; }
    public void setDistractorReason(String distractorReason) { this.distractorReason = distractorReason; }
}
