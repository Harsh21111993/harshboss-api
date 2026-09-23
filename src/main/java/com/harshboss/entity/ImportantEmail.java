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
 * A lightweight summary of an email the AI flagged as important.
 *
 * <p>This is NOT a full email — we don't store the body. We store only:
 * <ul>
 *   <li>Who sent it + when (from the provider)</li>
 *   <li>The AI's brief (1-2 sentence summary)</li>
 *   <li>Why it's important (reason + score)</li>
 *   <li>A deep link to view the full email in Gmail/Outlook</li>
 * </ul>
 *
 * <p>The user clicks "View in Gmail" to read the full email in their provider's UI.
 * This keeps our DB lightweight — we're an intelligence layer, not a mailbox clone.</p>
 */
@Entity
@Table(name = "important_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportantEmail {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Which provider this email came from ("MICROSOFT" or "GOOGLE"). */
    @Column(nullable = false)
    private String provider;

    /** The email's ID in the provider's system (Gmail message ID or Graph message ID). */
    @Column(name = "provider_message_id", nullable = false)
    private String providerMessageId;

    /** Deep link to view this email in the provider's web UI. */
    @Column(name = "provider_url")
    private String providerUrl;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "from_name", nullable = false)
    private String fromName;

    @Column(nullable = false)
    private String subject;

    /** AI-generated 1-2 sentence summary (the only content we store). */
    @Column(nullable = false)
    private String brief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailLabel label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Importance importance;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String reason;

    @Column(name = "suggested_action", nullable = false)
    private String suggestedAction;

    /** JSON array of action items. */
    @Column(name = "action_items", nullable = false)
    private String actionItems;

    /** When the email was received (from the provider). */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** When our AI flagged this email as important. */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** True if this email also triggered a meeting-invitation calendar event. */
    @Column(name = "is_meeting_invitation", nullable = false)
    private boolean meetingInvitation;
}
