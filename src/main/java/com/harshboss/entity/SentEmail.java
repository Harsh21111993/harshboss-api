package com.harshboss.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An outbound notification email that Harsh-Boss drafted and "sent" (currently
 * mocked — the body is persisted and surfaced in the Approvals view).
 */
@Entity
@Table(name = "sent_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SentEmail {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "to_name")
    private String toName;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private String reason;

    @Column(name = "related_approval_id")
    private UUID relatedApprovalId;

    /** Workspace owner this sent email belongs to. */
    @Column(name = "user_id")
    private UUID userId;
}
