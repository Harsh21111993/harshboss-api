package com.harshboss.dto;

import com.harshboss.entity.enums.EventStatus;
import com.harshboss.entity.enums.Platform;

import java.time.Instant;
import java.util.List;

/**
 * Calendar event from any platform, normalized to a single shape.
 *
 * @param sourceEmailId  if non-null, this event was auto-created from a meeting
 *                       invitation email — the UI shows a "View source email" link
 * @param sourceEmailUrl deep link to view the source email in Gmail/Outlook
 */
public record CalendarEventDto(
        String id,
        String title,
        Platform platform,
        Instant start,
        Instant end,
        String organizer,
        List<String> attendees,
        String joinUrl,
        String location,
        boolean isHiddenByOthers,
        EventStatus status,
        String sourceEmailId,
        String sourceEmailUrl
) {}
