package com.harshboss.ai;

import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.ThreadSummary;
import com.harshboss.entity.ImportantEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feature 5: Email Thread Summarization.
 *
 * Compresses long email chains into 3 bullets:
 *   • What was decided
 *   • What's pending
 *   • What you need to do
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadSummaryAiService {

    private final ChatClient chatClient;

    private static final String SYSTEM = """
            You are an executive assistant summarizing email threads. Given a list of emails
            on the same subject, produce a summary with exactly 3 sections:

            DECIDED: What has been decided or confirmed so far.
            PENDING: What is still open or waiting on someone.
            ACTION: What the user specifically needs to do next.

            Keep each section to 1-2 sentences. Plain text only. No markdown.
            """;

    public ThreadSummary summarize(List<ImportantEmail> emails, String threadSubject) {
        if (emails == null || emails.isEmpty()) return null;

        StringBuilder emailText = new StringBuilder();
        emailText.append("Thread subject: ").append(threadSubject).append("\n\n");
        for (int i = 0; i < emails.size(); i++) {
            ImportantEmail e = emails.get(i);
            emailText.append("--- Email %d ---\n".formatted(i + 1));
            emailText.append("From: %s\n".formatted(e.getFromName()));
            emailText.append("Date: %s\n".formatted(e.getReceivedAt()));
            emailText.append("Subject: %s\n".formatted(e.getSubject()));
            emailText.append("Summary: %s\n".formatted(e.getBrief()));
            emailText.append("Action items: %s\n\n".formatted(e.getActionItems()));
        }

        try {
            String summary = chatClient.prompt()
                    .system(SYSTEM)
                    .user(emailText.toString())
                    .call()
                    .content();

            if (summary == null || summary.isBlank()) return null;

            // Parse the 3 sections
            String decided = extractSection(summary, "DECIDED");
            String pending = extractSection(summary, "PENDING");
            String action = extractSection(summary, "ACTION");

            ThreadSummary ts = new ThreadSummary();
            ts.setThreadSubject(threadSubject);
            ts.setDecided(decided);
            ts.setPending(pending);
            ts.setActionNeeded(action);
            ts.setEmailCount(emails.size());
            // userId will be set by the caller
            return ts;
        } catch (Exception e) {
            log.error("Thread summary failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractSection(String text, String sectionName) {
        String marker = sectionName + ":";
        int start = text.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        // Find the next section or end
        int end = text.length();
        for (String next : List.of("DECIDED:", "PENDING:", "ACTION:")) {
            if (!next.equals(marker)) {
                int nextStart = text.indexOf(next, start);
                if (nextStart > 0 && nextStart < end) end = nextStart;
            }
        }
        return text.substring(start, end).trim();
    }
}
