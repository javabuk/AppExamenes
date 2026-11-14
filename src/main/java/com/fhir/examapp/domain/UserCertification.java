package com.fhir.examapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "user_certifications", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "certification_id"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id", nullable = false)
    private Certification certification;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "target_exam_date")
    private String targetExamDate;

    @Column(name = "enrolled_at", nullable = false)
    private String enrolledAt;

    @Column(name = "assigned_by")
    private String assignedBy;

    public UserCertification() {
        this.enrolledAt = formatNow();
    }

    public UserCertification(User user, Certification certification) {
        this(user, certification, "ADMIN");
    }

    public UserCertification(User user, Certification certification, String assignedBy) {
        this();
        this.user = user;
        this.certification = certification;
        this.assignedBy = assignedBy;
        this.active = true;
    }

    public static String formatNow() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Certification getCertification() { return certification; }
    public void setCertification(Certification certification) { this.certification = certification; }

    public boolean isActive() { return Boolean.TRUE.equals(active); }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getTargetExamDate() { return targetExamDate; }
    public void setTargetExamDate(String targetExamDate) { this.targetExamDate = targetExamDate; }

    public String getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(String enrolledAt) { this.enrolledAt = enrolledAt; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
}
