package com.fhir.examapp.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "study_materials")
public class StudyMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private SyllabusTopic topic;

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

    public StudyMaterial() {}

    public StudyMaterial(SyllabusTopic topic, String sourceType, String title, String contentText, String referenceUrlOrPage) {
        this.topic = topic;
        this.sourceType = sourceType;
        this.title = title;
        this.contentText = contentText;
        this.referenceUrlOrPage = referenceUrlOrPage;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SyllabusTopic getTopic() { return topic; }
    public void setTopic(SyllabusTopic topic) { this.topic = topic; }

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
}
