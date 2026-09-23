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
@Table(name = "thread_summaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ThreadSummary {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "thread_subject", nullable = false)
    private String threadSubject;

    @Column(nullable = false)
    private String decided;

    @Column(nullable = false)
    private String pending;

    @Column(name = "action_needed", nullable = false)
    private String actionNeeded;

    @Column(name = "email_count", nullable = false)
    private int emailCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
