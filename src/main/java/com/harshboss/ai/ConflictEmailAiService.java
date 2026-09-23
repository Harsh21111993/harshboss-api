package com.harshboss.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drafts the polite "I have a conflict, here are some alternative slots" email
 * that Harsh-Boss auto-sends when a proposed meeting time overlaps an existing event.
 *
 * <p>The draft is persisted on the {@link com.harshboss.entity.Approval} and shown
 * to the user — no real SMTP send happens in the prototype.</p>
 */
@Slf4j
@Service
public class ConflictEmailAiService {

    private final ChatClient chatClient;
    private final String systemPrompt;

    public ConflictEmailAiService(ChatClient chatClient,
                                  @Value("classpath:prompts/conflict-email.st") Resource promptResource)
            throws IOException {
        this.chatClient = chatClient;
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * @param requesterName   who asked for the meeting
     * @param requestedTime   the proposed (now-conflicted) start time
     * @param conflictSummary short description of the overlapping event
     * @param alternatives    2-3 alternative start times (ISO 8601 strings)
     * @param ownerName       the workspace owner's name (dynamic, used in the sign-off)
     * @return the email body (plain text); never null
     */
    public String draftConflictReply(String requesterName,
                                     Instant requestedTime,
                                     String conflictSummary,
                                     List<String> alternatives,
                                     String ownerName) {
        try {
            String userMsg = """
                    Requester name: %s
                    Requested time (ISO): %s
                    Conflict summary: %s
                    Alternative slots (ISO):
                    %s
                    Sign off the email as: %s
                    """.formatted(
                    requesterName == null ? "there" : requesterName,
                    requestedTime == null ? "the proposed time" : requestedTime.toString(),
                    conflictSummary == null ? "an existing calendar block" : conflictSummary,
                    alternatives == null ? "(none)" : String.join("\n", alternatives),
                    ownerName == null ? "Harsh-Boss Assistant" : ownerName
            );

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMsg)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return fallback(requesterName, alternatives, ownerName);
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("AI conflict email draft failed: {}", e.getMessage());
            return fallback(requesterName, alternatives, ownerName);
        }
    }

    /** Plain-text template fallback — keeps the workflow usable without a working LLM. */
    private String fallback(String requesterName, List<String> alternatives, String ownerName) {
        String signoff = (ownerName == null || ownerName.isBlank()) ? "Harsh-Boss Assistant" : ownerName;
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(requesterName == null ? "there" : requesterName).append(",\n\n");
        sb.append("Thanks for reaching out. Unfortunately I have a calendar conflict at the time you proposed.\n");
        if (alternatives != null && !alternatives.isEmpty()) {
            sb.append("Could any of these work instead?\n\n");
            for (String slot : alternatives) {
                sb.append("  • ").append(slot).append("\n");
            }
            sb.append("\nLet me know which works and I'll send a calendar invite.\n\n");
        } else {
            sb.append("Could you suggest a couple of other times? Happy to make it work.\n\n");
        }
        sb.append("Best,\n").append(signoff);
        return sb.toString();
    }
}
