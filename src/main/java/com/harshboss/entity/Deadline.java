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
@Table(name = "deadlines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Deadline {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "important_email_id")
    private UUID importantEmailId;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "source_subject")
    private String sourceSubject;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING | DONE | OVERDUE

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
