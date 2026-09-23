package com.harshboss.ai;

import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.ImportantEmail;
import com.harshboss.entity.MeetingPrepBrief;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Feature 4: Meeting Prep Brief.
 *
 * 15 minutes before a meeting, generates a brief containing:
 *   • Attendee history (who they are, last interaction)
 *   • Relevant emails (emails from attendees in the last week)
 *   • Last meeting's action items (if any)
 *   • The agenda (extracted from the event title or email)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingPrepAiService {

    private final ChatClient chatClient;

    private static final String SYSTEM = """
            You are an executive assistant preparing a meeting brief. Given the meeting details
            and relevant emails, produce a concise brief with:

            AGENDA: What this meeting is about (inferred from the title + emails).
            CONTEXT: Key background from the relevant emails.
            PREP: What the user should prepare or review before the meeting.

            Keep it under 200 words. Plain text, no markdown.
            """;

    public MeetingPrepBrief generateBrief(CalendarEvent event, List<ImportantEmail> relevantEmails, UUID userId) {
        StringBuilder context = new StringBuilder();
        context.append("Meeting: ").append(event.getTitle()).append("\n");
        context.append("Start: ").append(event.getStartTime()).append("\n");
        context.append("Organizer: ").append(event.getOrganizer()).append("\n");
        context.append("Attendees: ").append(event.getAttendees()).append("\n\n");

        if (relevantEmails != null && !relevantEmails.isEmpty()) {
            context.append("Relevant emails from attendees:\n");
            for (ImportantEmail e : relevantEmails) {
                context.append("- From %s: %s (brief: %s)\n".formatted(
                        e.getFromName(), e.getSubject(), e.getBrief()));
            }
        } else {
            context.append("No recent emails from attendees found.\n");
        }

        try {
            String brief = chatClient.prompt()
                    .system(SYSTEM)
                    .user(context.toString())
                    .call()
                    .content();

            MeetingPrepBrief mpb = new MeetingPrepBrief();
            mpb.setUserId(userId);
            mpb.setCalendarEventId(event.getId());
            mpb.setMeetingTitle(event.getTitle());
            mpb.setMeetingStart(event.getStartTime());
            mpb.setAttendees(event.getAttendees());
            mpb.setRelevantEmails(JsonUtil.toJson(relevantEmails.stream()
                    .map(e -> e.getSubject() + " — " + e.getBrief())
                    .toList()));
            mpb.setAgenda(brief != null ? brief.trim() : "Could not generate agenda.");
            return mpb;
        } catch (Exception e) {
            log.error("Meeting prep brief failed: {}", e.getMessage());
            return null;
        }
    }
}
