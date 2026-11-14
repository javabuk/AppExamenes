package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "certifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // Ej: FHIR_R4, FHIR_R5

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "standard_version", nullable = false)
    private String standardVersion = "R4"; // R4, R5, V2, OTHER

    @Column(name = "exam_question_count", nullable = false)
    private Integer examQuestionCount = 50;

    @Column(name = "exam_duration_minutes", nullable = false)
    private Integer examDurationMinutes = 90;

    @Column(name = "passing_score_percent", nullable = false)
    private Double passingScorePercent = 70.0;

    @Column(name = "spec_base_url")
    private String specBaseUrl = "https://hl7.org/fhir/R4/";

    @Column(name = "feature_flags", nullable = false)
    private String featureFlags = "REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS";

    @Column(length = 50)
    private String color = "#3b82f6";

    @Column(length = 20)
    private String icon = "🧬";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @OneToMany(mappedBy = "certification", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @JsonIgnore
    private List<CertificationArea> areas = new ArrayList<>();

    public Certification() {
        this.createdAt = formatNow();
    }

    public Certification(String code, String name, String standardVersion,
                         Integer examQuestionCount, Integer examDurationMinutes,
                         Double passingScorePercent, String specBaseUrl) {
        this();
        this.code = code;
        this.name = name;
        this.standardVersion = standardVersion;
        this.examQuestionCount = examQuestionCount;
        this.examDurationMinutes = examDurationMinutes;
        this.passingScorePercent = passingScorePercent;
        this.specBaseUrl = specBaseUrl;
    }

    public static String formatNow() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public boolean hasFeature(String feature) {
        if (featureFlags == null || feature == null) return false;
        return Arrays.stream(featureFlags.split(","))
                .map(String::trim)
                .anyMatch(f -> f.equalsIgnoreCase(feature.trim()));
    }

    public List<String> getFeatureList() {
        if (featureFlags == null || featureFlags.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(featureFlags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setFeatureList(List<String> features) {
        if (features == null || features.isEmpty()) {
            this.featureFlags = "";
        } else {
            this.featureFlags = String.join(",", features);
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }

    public Integer getExamQuestionCount() { return examQuestionCount; }
    public void setExamQuestionCount(Integer examQuestionCount) { this.examQuestionCount = examQuestionCount; }

    public Integer getExamDurationMinutes() { return examDurationMinutes; }
    public void setExamDurationMinutes(Integer examDurationMinutes) { this.examDurationMinutes = examDurationMinutes; }

    public Double getPassingScorePercent() { return passingScorePercent; }
    public void setPassingScorePercent(Double passingScorePercent) { this.passingScorePercent = passingScorePercent; }

    public String getSpecBaseUrl() { return specBaseUrl; }
    public void setSpecBaseUrl(String specBaseUrl) { this.specBaseUrl = specBaseUrl; }

    public String getFeatureFlags() { return featureFlags; }
    public void setFeatureFlags(String featureFlags) { this.featureFlags = featureFlags; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public String getVersion() { return standardVersion; }
    public void setVersion(String standardVersion) { this.standardVersion = standardVersion; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<CertificationArea> getAreas() { return areas; }
    public void setAreas(List<CertificationArea> areas) { this.areas = areas; }
}
