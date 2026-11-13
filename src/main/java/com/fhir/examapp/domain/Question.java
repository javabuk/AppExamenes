package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private SyllabusTopic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id")
    @JsonIgnore
    private Question parentQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private StudyMaterial material;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private String source; // 'MOCK_EXAM', 'AI_GENERATED', 'MANUAL'

    @Column(name = "question_type", nullable = false)
    private String questionType; // 'SINGLE_CHOICE', 'MULTI_CHOICE', 'FHIR_URL'

    @Column(nullable = false)
    private Integer difficulty = 2; // 1 (Básico), 2 (Examen), 3 (Avanzado)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(name = "code_snippet", columnDefinition = "TEXT")
    private String codeSnippet;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "fhir_spec_url")
    private String fhirSpecUrl;

    @Column(name = "is_verified")
    private Boolean verified = true;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private String deletedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionOption> options = new ArrayList<>();

    public Question() {}

    public void addOption(QuestionOption option) {
        options.add(option);
        option.setQuestion(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SyllabusTopic getTopic() { return topic; }
    public void setTopic(SyllabusTopic topic) { this.topic = topic; }

    public Question getParentQuestion() { return parentQuestion; }
    public void setParentQuestion(Question parentQuestion) { this.parentQuestion = parentQuestion; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    @JsonProperty("ownerId")
    public Long resolveOwnerId() {
        return owner != null ? owner.getId() : null;
    }

    @JsonProperty("ownerUsername")
    public String resolveOwnerUsername() {
        return owner != null ? owner.getUsername() : null;
    }

    @JsonProperty("ownerDisplayName")
    public String resolveOwnerDisplayName() {
        return owner != null ? owner.getDisplayName() : null;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getFhirSpecUrl() { return fhirSpecUrl; }
    public void setFhirSpecUrl(String fhirSpecUrl) { this.fhirSpecUrl = fhirSpecUrl; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public StudyMaterial getMaterial() { return material; }
    public void setMaterial(StudyMaterial material) { this.material = material; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public List<QuestionOption> getOptions() { return options; }
    public void setOptions(List<QuestionOption> options) { this.options = options; }
}
