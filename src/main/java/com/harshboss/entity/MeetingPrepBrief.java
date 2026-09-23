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
@Table(name = "meeting_prep_briefs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MeetingPrepBrief {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "calendar_event_id")
    private UUID calendarEventId;

    @Column(name = "meeting_title", nullable = false)
    private String meetingTitle;

    @Column(name = "meeting_start", nullable = false)
    private Instant meetingStart;

    @Column(nullable = false)
    private String attendees; // JSON array

    @Column(name = "relevant_emails", nullable = false)
    private String relevantEmails; // JSON array of summaries

    @Column(name = "last_meeting_notes")
    private String lastMeetingNotes;

    @Column
    private String agenda;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
