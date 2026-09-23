package com.harshboss.entity;

import com.harshboss.entity.enums.EventStatus;
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
 * A calendar event from any platform (Teams, Google, Zoom, Personal, or a
 * BLOCKED slot others have marked busy without sharing details).
 */
@Entity
@Table(name = "calendar_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    private String organizer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String attendees;          // JSON array string

    @Column(name = "join_url")
    private String joinUrl;

    @Column(name = "location")
    private String location;

    @Column(name = "is_hidden_by_others", nullable = false)
    private boolean isHiddenByOthers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    /** Workspace owner this event belongs to. Nullable for backward compat with pre-V4 rows. */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * If this event was auto-created from a meeting/interview invitation email,
     * this points to the source email. Null for manually-created or synced events.
     * The UI uses this to show a "View source email" link.
     */
    @Column(name = "source_email_id")
    private UUID sourceEmailId;

    /**
     * Deep link to view the source email in the provider's web UI (Gmail/Outlook).
     * Used when sourceEmailId is null (we don't store the full email, only the link).
     */
    @Column(name = "source_email_url")
    private String sourceEmailUrl;
}
