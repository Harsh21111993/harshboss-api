package com.harshboss.entity;

import com.harshboss.entity.enums.ApprovalStatus;
import com.harshboss.entity.enums.Platform;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending approval (currently used for meeting proposals). When a conflict
 * is detected, the conflict fields and auto-reply email are populated and 2-3
 * alternative slots are offered. On decision, decisionEmailSent is filled in.
 */
@Entity
@Table(name = "approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Approval {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String type;               // "MEETING_PROPOSAL"

    @Column(name = "requester_name", nullable = false)
    private String requesterName;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "requested_time", nullable = false)
    private Instant requestedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "proposal_title", nullable = false)
    private String proposalTitle;

    @Column(name = "proposed_start", nullable = false)
    private Instant proposedStart;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "conflict_event_title")
    private String conflictEventTitle;

    @Column(name = "conflict_event_start")
    private Instant conflictEventStart;

    @Column(name = "conflict_event_end")
    private Instant conflictEventEnd;

    @Column(columnDefinition = "TEXT")
    private String alternatives;       // JSON array string of ISO timestamps

    @Column(name = "auto_reply_sent", columnDefinition = "TEXT")
    private String autoReplySent;

    @Column(name = "decision_email_sent", columnDefinition = "TEXT")
    private String decisionEmailSent;

    /** Workspace owner this approval belongs to. Nullable for backward compat with pre-V4 rows. */
    @Column(name = "user_id")
    private UUID userId;
}
