package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "study_materials")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StudyMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private SyllabusTopic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id")
    private Certification certification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private CertificationArea area;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "source_type", nullable = false)
    private String sourceType; // 'COURSE_PDF', 'OFFICIAL_FHIR_SPEC', 'MANUAL'

    @Column(nullable = false)
    private String title;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "reference_url_or_page")
    private String referenceUrlOrPage;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private String deletedAt;

    @Transient
    private long linkedQuestionsCount;

    @Transient
    private long commentsCount;

    public StudyMaterial() {}

    public StudyMaterial(SyllabusTopic topic, String sourceType, String title, String contentText, String referenceUrlOrPage) {
        this.topic = topic;
        this.sourceType = sourceType;
        this.title = title;
        this.contentText = contentText;
        this.referenceUrlOrPage = referenceUrlOrPage;
        this.createdAt = User.formatNow();
        this.deleted = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SyllabusTopic getTopic() { return topic; }
    public void setTopic(SyllabusTopic topic) { this.topic = topic; }

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

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }

    public String getReferenceUrlOrPage() { return referenceUrlOrPage; }
    public void setReferenceUrlOrPage(String referenceUrlOrPage) { this.referenceUrlOrPage = referenceUrlOrPage; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public long getLinkedQuestionsCount() { return linkedQuestionsCount; }
    public void setLinkedQuestionsCount(long linkedQuestionsCount) { this.linkedQuestionsCount = linkedQuestionsCount; }

    public long getCommentsCount() { return commentsCount; }
    public void setCommentsCount(long commentsCount) { this.commentsCount = commentsCount; }

    public Certification getCertification() { return certification; }
    public void setCertification(Certification certification) { this.certification = certification; }

    public CertificationArea getArea() { return area; }
    public void setArea(CertificationArea area) { this.area = area; }
}
