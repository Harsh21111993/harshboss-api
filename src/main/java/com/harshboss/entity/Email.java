package com.harshboss.entity;

import com.harshboss.entity.enums.EmailFolder;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An inbound email, either filed in INBOX or SPAM by the email client.
 */
@Entity
@Table(name = "emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Email {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "from_name", nullable = false)
    private String fromName;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailFolder folder;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Workspace owner this email belongs to. Nullable for backward compat with pre-V4 rows. */
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private EmailAnalysis analysis;
}
