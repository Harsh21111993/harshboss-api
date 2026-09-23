package com.harshboss.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Drafts the meeting-decision notification email sent when the user approves
 * or declines a pending approval. Two variants (approve / decline) are handled
 * by the same prompt — the decision is passed in the user message.
 */
@Slf4j
@Service
public class DecisionEmailAiService {

    private final ChatClient chatClient;
    private final String systemPrompt;

    public DecisionEmailAiService(ChatClient chatClient,
                                  @Value("classpath:prompts/decision-email.st") Resource promptResource)
            throws IOException {
        this.chatClient = chatClient;
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * @param decision        "APPROVE" or "DECLINE"
     * @param requesterName   who proposed the meeting
     * @param meetingTitle    title of the proposed meeting
     * @param requestedTime   proposed start time
     * @param alternativeNote optional note about chosen alternative (may be null/blank)
     * @param ownerName       the workspace owner's name (dynamic, used in the sign-off)
     * @return the email body (plain text); never null
     */
    public String draftDecisionEmail(String decision,
                                     String requesterName,
                                     String meetingTitle,
                                     Instant requestedTime,
                                     String alternativeNote,
                                     String ownerName) {
        try {
            String userMsg = """
                    Decision: %s
                    Requester name: %s
                    Meeting title: %s
                    Requested time (ISO): %s
                    Alternative note: %s
                    Sign off the email as: %s
                    """.formatted(
                    decision == null ? "APPROVE" : decision,
                    requesterName == null ? "there" : requesterName,
                    meetingTitle == null ? "your meeting" : meetingTitle,
                    requestedTime == null ? "the proposed time" : requestedTime.toString(),
                    alternativeNote == null || alternativeNote.isBlank() ? "(none)" : alternativeNote,
                    ownerName == null ? "Harsh-Boss Assistant" : ownerName
            );

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMsg)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return fallback(decision, requesterName, meetingTitle, requestedTime, ownerName);
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("AI decision email draft failed: {}", e.getMessage());
            return fallback(decision, requesterName, meetingTitle, requestedTime, ownerName);
        }
    }

    private String fallback(String decision, String requesterName, String meetingTitle, Instant requestedTime, String ownerName) {
        String signoff = (ownerName == null || ownerName.isBlank()) ? "Harsh-Boss Assistant" : ownerName;
        boolean approve = !"DECLINE".equalsIgnoreCase(decision);
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(requesterName == null ? "there" : requesterName).append(",\n\n");
        if (approve) {
            sb.append("Confirming our meeting \"").append(meetingTitle == null ? "" : meetingTitle)
              .append("\" at ").append(requestedTime == null ? "the proposed time" : requestedTime.toString())
              .append(". A calendar invite will follow shortly.\n\n");
        } else {
            sb.append("Thanks for proposing the meeting \"").append(meetingTitle == null ? "" : meetingTitle)
              .append("\". Unfortunately I won't be able to make it work right now — let's revisit next week.\n\n");
        }
        sb.append("Best,\n").append(signoff);
        return sb.toString();
    }
}
