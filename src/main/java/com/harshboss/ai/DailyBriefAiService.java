package com.harshboss.ai;

import com.harshboss.dto.CalendarEventDto;
import com.harshboss.dto.EmailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates the AI chief-of-staff Daily Brief — a 4-6 bullet summary of what
 * needs attention today, based on the day's important emails and meetings.
 */
@Slf4j
@Service
public class DailyBriefAiService {

    private final ChatClient chatClient;
    private final String systemPromptTemplate;

    public DailyBriefAiService(ChatClient chatClient,
                               @Value("classpath:prompts/daily-brief.st") Resource promptResource)
            throws IOException {
        this.chatClient = chatClient;
        // The .st file uses {importantEmails} / {todaysMeetings} placeholders.
        this.systemPromptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * @param importantEmails today's high/medium-importance emails (already triaged)
     * @param todaysMeetings  today's calendar events
     * @return the brief text (4-6 bullets); never null/empty
     */
    public String generate(List<EmailDto> importantEmails, List<CalendarEventDto> todaysMeetings) {
        try {
            String importantBlock = formatEmails(importantEmails);
            String meetingsBlock  = formatMeetings(todaysMeetings);

            String systemPrompt = systemPromptTemplate
                    .replace("{importantEmails}", importantBlock)
                    .replace("{todaysMeetings}", meetingsBlock);

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Generate today's daily brief.")
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return fallback(importantEmails, todaysMeetings);
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("AI daily brief generation failed: {}", e.getMessage());
            return fallback(importantEmails, todaysMeetings);
        }
    }

    private String formatEmails(List<EmailDto> emails) {
        if (emails == null || emails.isEmpty()) return "(no important emails today)";
        StringBuilder sb = new StringBuilder();
        for (EmailDto e : emails) {
            sb.append("- from ").append(e.fromName())
              .append(" <").append(e.from()).append(">")
              .append(" — ").append(e.subject())
              .append(" [label=").append(e.analysis() == null ? "untriaged" : e.analysis().label())
              .append(", importance=").append(e.analysis() == null ? "unknown" : e.analysis().importance())
              .append(", score=").append(e.analysis() == null ? "?" : e.analysis().score())
              .append("]\n");
        }
        return sb.toString();
    }

    private String formatMeetings(List<CalendarEventDto> events) {
        if (events == null || events.isEmpty()) return "(no meetings today)";
        StringBuilder sb = new StringBuilder();
        for (CalendarEventDto ev : events) {
            sb.append("- ").append(ev.start()).append(" → ").append(ev.end())
              .append(" : ").append(ev.title())
              .append(" [platform=").append(ev.platform())
              .append(", status=").append(ev.status()).append("]\n");
        }
        return sb.toString();
    }

    /** Plain-text fallback brief so the dashboard never shows an empty card. */
    private String fallback(List<EmailDto> emails, List<CalendarEventDto> meetings) {
        StringBuilder sb = new StringBuilder();
        if (emails != null && !emails.isEmpty()) {
            sb.append("• ").append(emails.size()).append(" important email(s) need your attention today.\n");
        }
        if (meetings != null && !meetings.isEmpty()) {
            sb.append("• ").append(meetings.size()).append(" meeting(s) on your calendar today.\n");
        }
        if (sb.isEmpty()) {
            sb.append("• Inbox zero and no meetings today — enjoy the breathing room.\n");
        }
        sb.append("• Review the Inbox tab to triage anything AI couldn't classify.\n");
        return sb.toString();
    }
}
