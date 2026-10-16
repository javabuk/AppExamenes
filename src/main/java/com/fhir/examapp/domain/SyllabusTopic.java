package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "syllabus_topics")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SyllabusTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "exam_weight_percent", nullable = false)
    private Double examWeightPercent;

    @Column(name = "study_status")
    private String studyStatus = "PENDING";

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Question> questions = new ArrayList<>();

    public SyllabusTopic() {}

    public SyllabusTopic(String code, String name, Double examWeightPercent, String studyStatus) {
        this.code = code;
        this.name = name;
        this.examWeightPercent = examWeightPercent;
        this.studyStatus = studyStatus;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getExamWeightPercent() { return examWeightPercent; }
    public void setExamWeightPercent(Double examWeightPercent) { this.examWeightPercent = examWeightPercent; }

    public String getStudyStatus() { return studyStatus; }
    public void setStudyStatus(String studyStatus) { this.studyStatus = studyStatus; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}
