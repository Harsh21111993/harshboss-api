package com.harshboss.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Contact {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "last_replied_at")
    private Instant lastRepliedAt;

    @Column(name = "total_emails", nullable = false)
    private int totalEmails;

    @Column(name = "total_replies", nullable = false)
    private int totalReplies;

    @Column(name = "avg_response_hours")
    private BigDecimal avgResponseHours;

    @Column(name = "relationship_health", nullable = false)
    private String relationshipHealth = "GOOD"; // GOOD | STALE | AT_RISK | COLD

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
