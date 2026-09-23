package com.harshboss.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects meeting/interview invitations in emails and extracts the details
 * (start time, duration, join URL, location, title).
 *
 * <p>Two-stage approach:
 * <ol>
 *   <li><b>Fast regex pre-filter</b> — if the email body doesn't contain any
 *       meeting-related keywords (meeting, interview, call, schedule, invite,
 *       calendar, zoom, teams, meet, etc.), skip the LLM call entirely.
 *       This saves cost — most emails aren't meeting invitations.</li>
 *   <li><b>LLM extraction</b> — for emails that pass the filter, ask Gemini
 *       to extract the meeting details as JSON. The LLM handles natural
 *       language date/time parsing ("next Tuesday at 3pm", "Jan 15, 2:00 PM IST")
 *       which is extremely hard to do reliably with regex alone.</li>
 * </ol>
 *
 * <p>When a meeting is detected, the {@link com.harshboss.service.EmailService}
 * auto-creates a {@link com.harshboss.entity.CalendarEvent} linked to the source
 * email via {@code sourceEmailId}. The UI then shows a "View source email"
 * link on the calendar event.</p>
 */
@Slf4j
@Service
public class MeetingInvitationDetector {

    private final ChatClient chatClient;

    /** Fast regex pre-filter — if none of these words appear, skip the LLM call. */
    private static final Pattern MEETING_KEYWORDS = Pattern.compile(
            "(?i)\\b(meeting|interview|call|schedule|invited|calendar|zoom|teams|"
            + "google meet|hangout|join us|join the|video call|phone screen|"
            + "technical round|hr round|onsite|virtual|slot|time slot|"
            + "date.*time|when.*where|appointment|booked|confirmed.*time)\\b"
    );

    /** URL patterns for join links. */
    private static final Pattern JOIN_URL = Pattern.compile(
            "https?://[\\w.-]+\\.[a-z]{2,}[/\\w.?=&%#-]*(?:zoom|teams|meet|hangout|webex|whereby|gotomeeting)[\\w/?=&%#-]*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String SYSTEM_PROMPT = """
            You are a meeting invitation detector. Analyze the email and determine
            if it contains a meeting, interview, or call invitation with a specific
            date and time.

            If it IS a meeting invitation, extract the details and respond with
            valid JSON only:
            {
              "isMeeting": true,
              "title": "short title for the calendar event (e.g. 'Interview — Tech Round')",
              "startDateTime": "ISO 8601 with timezone offset, e.g. 2025-01-15T14:00:00+05:30",
              "durationMinutes": 30,
              "platform": "TEAMS | GOOGLE | ZOOM | PERSONAL",
              "joinUrl": "the meeting join URL if found, else null",
              "location": "physical location if mentioned, else null"
            }

            If it is NOT a meeting invitation (no specific date/time), respond with:
            { "isMeeting": false }

            Rules:
            - Only return isMeeting=true if there is a SPECIFIC date AND time mentioned.
              Vague phrases like "sometime next week" → isMeeting=false.
            - For the timezone, use the timezone mentioned in the email. If none,
              use Asia/Calcutta (IST, +05:30) as the default.
            - If duration isn't explicitly stated, infer from the meeting type
              (interview=60, quick call=30, default=30).
            - Platform: detect from the join URL (zoom.us → ZOOM, teams.microsoft → TEAMS,
              meet.google → GOOGLE, else PERSONAL).
            - Respond with valid JSON only, no markdown fences, no prose.
            """;

    public MeetingInvitationDetector(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Analyze an email for a meeting invitation.
     * Returns null if no meeting is detected.
     */
    public MeetingInvitation detect(String subject, String body, String fromName) {
        String fullText = (subject != null ? subject : "") + "\n" + (body != null ? body : "");

        // 1. Fast regex pre-filter — skip the LLM call if no meeting keywords
        if (!MEETING_KEYWORDS.matcher(fullText).find()) {
            return null;
        }

        try {
            String userMsg = """
                    From: %s
                    Subject: %s

                    Body:
                    %s
                    """.formatted(fromName != null ? fromName : "Unknown", subject, body);

            MeetingInvitation result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMsg)
                    .call()
                    .entity(MeetingInvitation.class);

            if (result == null || !result.isMeeting) {
                return null;
            }

            // Validate the start time was parsed
            if (result.startDateTime == null || result.startDateTime.isBlank()) {
                log.debug("Meeting detected but no start time parsed for: {}", subject);
                return null;
            }

            // Try to extract a join URL via regex if the LLM didn't find one
            if (result.joinUrl == null || result.joinUrl.isBlank()) {
                Matcher m = JOIN_URL.matcher(fullText);
                if (m.find()) {
                    result.joinUrl = m.group();
                }
            }

            log.info("Meeting invitation detected: '{}' at {} ({} min, {})",
                    result.title, result.startDateTime, result.durationMinutes, result.platform);
            return result;
        } catch (Exception e) {
            log.warn("Meeting detection failed for '{}': {}", subject, e.getMessage());
            return null;
        }
    }

    /**
     * The extracted meeting details. Uses a mutable class (not a record) so
     * Spring AI can populate it via Jackson, and we can post-process the joinUrl.
     */
    @lombok.Data
    public static class MeetingInvitation {
        public boolean isMeeting;
        public String title;
        public String startDateTime;     // ISO 8601 with offset
        public int durationMinutes;
        public String platform;          // TEAMS | GOOGLE | ZOOM | PERSONAL
        public String joinUrl;
        public String location;
    }
}
