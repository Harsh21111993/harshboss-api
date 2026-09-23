package com.harshboss.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_portal_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class JobPortalApplication {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "resume_id") private UUID resumeId;
    @Column(nullable = false) private String portal; // NAUKRI | LINKEDIN | WELLFOUND | INSTAHYRE | OTTA | TURING | CUTSHORT
    @Column(name = "job_title", nullable = false) private String jobTitle;
    @Column private String company;
    @Column(name = "job_url") private String jobUrl;
    @Column private String location;
    @Column private String salary;
    @Column(name = "work_mode") private String workMode; // REMOTE | HYBRID | ONSITE
    @Column(nullable = false) private String status = "NOT_APPLIED";
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "interview_date") private Instant interviewDate;
    @Column(columnDefinition = "TEXT") private String notes;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
