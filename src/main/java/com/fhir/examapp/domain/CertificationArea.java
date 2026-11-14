package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certification_areas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CertificationArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id", nullable = false)
    @JsonIgnore
    private Certification certification;

    @Column(nullable = false)
    private String code; // Ej: FOUNDATIONS, REST_SEARCH

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "weight_percent", nullable = false)
    private Double weightPercent; // Peso oficial (%)

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 1;

    @Column(name = "study_status")
    private String studyStatus = "PENDING";

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "area", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Question> questions = new ArrayList<>();

    public CertificationArea() {}

    public CertificationArea(Certification certification, String code, String name,
                             Double weightPercent, Integer sortOrder) {
        this.certification = certification;
        this.code = code;
        this.name = name;
        this.weightPercent = weightPercent;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Certification getCertification() { return certification; }
    public void setCertification(Certification certification) { this.certification = certification; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getWeightPercent() { return weightPercent; }
    public void setWeightPercent(Double weightPercent) { this.weightPercent = weightPercent; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStudyStatus() { return studyStatus; }
    public void setStudyStatus(String studyStatus) { this.studyStatus = studyStatus; }

    public boolean isActive() { return Boolean.TRUE.equals(active); }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}
