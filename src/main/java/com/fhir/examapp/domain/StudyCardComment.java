package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "study_card_comments")
public class StudyCardComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private StudyMaterial material;

    @Column(name = "card_index", nullable = false)
    private Integer cardIndex;

    @Column(name = "card_heading")
    private String cardHeading;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    public StudyCardComment() {}

    public StudyCardComment(StudyMaterial material, Integer cardIndex, String cardHeading, String contentMarkdown, String createdAt) {
        this.material = material;
        this.cardIndex = cardIndex;
        this.cardHeading = cardHeading;
        this.contentMarkdown = contentMarkdown;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StudyMaterial getMaterial() { return material; }
    public void setMaterial(StudyMaterial material) { this.material = material; }

    @JsonProperty("materialId")
    public Long resolveMaterialId() {
        return material != null ? material.getId() : null;
    }

    public Integer getCardIndex() { return cardIndex; }
    public void setCardIndex(Integer cardIndex) { this.cardIndex = cardIndex; }

    public String getCardHeading() { return cardHeading; }
    public void setCardHeading(String cardHeading) { this.cardHeading = cardHeading; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
