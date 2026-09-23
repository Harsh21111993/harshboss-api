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
@Table(name = "resumes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Resume {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column private String email;
    @Column private String phone;
    @Column(name = "current_title") private String currentTitle;
    @Column(name = "years_experience") private Integer yearsExperience;
    @Column(nullable = false, columnDefinition = "TEXT") private String skills;
    @Column(nullable = false, columnDefinition = "TEXT") private String experience;
    @Column(nullable = false, columnDefinition = "TEXT") private String education;
    @Column private String location;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(name = "preferred_role") private String preferredRole;
    @Column(name = "preferred_location") private String preferredLocation;
    @Column(name = "salary_expectation") private String salaryExpectation;
    @Column(name = "embedding_id") private String embeddingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
