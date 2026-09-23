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
@Table(name = "slack_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SlackConfig {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "webhook_url", nullable = false)
    private String webhookUrl;

    @Column
    private String channel; // #important-emails

    @Column(name = "notify_high_only", nullable = false)
    private boolean notifyHighOnly = true;

    @Column(name = "auto_forward", nullable = false)
    private boolean autoForward = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
