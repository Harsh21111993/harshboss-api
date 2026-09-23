package com.harshboss.entity;

import com.harshboss.entity.enums.EmailLabel;
import com.harshboss.entity.enums.Importance;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * AI triage result for a single email (1:1 with {@link Email}).
 * The list fields (action_items, key_dates) are stored as TEXT JSON strings
 * and converted with {@link com.harshboss.dto.JsonUtil}.
 */
@Entity
@Table(name = "email_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailAnalysis {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id", nullable = false, unique = true)
    private Email email;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String brief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailLabel label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Importance importance;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "suggested_action", nullable = false, columnDefinition = "TEXT")
    private String suggestedAction;

    @Column(name = "action_items", nullable = false, columnDefinition = "TEXT")
    private String actionItems;        // JSON array string

    @Column(name = "key_dates", nullable = false, columnDefinition = "TEXT")
    private String keyDates;           // JSON array string

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;
}
