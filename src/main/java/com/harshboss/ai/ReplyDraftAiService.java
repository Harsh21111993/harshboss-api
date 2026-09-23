package com.harshboss.ai;
import com.harshboss.aspect.annotation.RetryOnFailure;
import com.harshboss.aspect.annotation.MeasurePerformance;

import com.harshboss.entity.ImportantEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Feature 1: AI Reply Drafting.
 *
 * Generates 3 reply options for an important email:
 *   • ACCEPT — agree / confirm / approve
 *   • DECLINE — politely decline / push back
 *   • CUSTOM — a balanced, neutral reply
 *
 * The user picks one, tweaks it, and sends — without typing from scratch.
 */
@Slf4j
@Service
public class ReplyDraftAiService {

    private final ChatClient chatClient;

    private static final String SYSTEM = """
            You are an executive assistant drafting email replies. Write concise, professional
            replies (3-5 sentences). Match the tone of the original email. Sign off as the user.
            Respond with plain text only — no markdown, no headers, no "Subject:" line.
            """;

    public ReplyDraftAiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Draft a reply with the given tone.
     * @param email the important email to reply to
     * @param tone  "ACCEPT" | "DECLINE" | "CUSTOM"
     */
    @RetryOnFailure(maxAttempts = 3, delayMs = 1000)
    @MeasurePerformance(warnThresholdMs = 10000)
    public String draftReply(ImportantEmail email, String tone) {
        String toneInstruction = switch (tone.toUpperCase()) {
            case "ACCEPT" -> "Write a reply that ACCEPTS or agrees with the sender's request. Be enthusiastic and confirm next steps.";
            case "DECLINE" -> "Write a reply that POLITELY DECLINES or pushes back. Be respectful, give a brief reason, and offer an alternative if possible.";
            default -> "Write a balanced, neutral reply that acknowledges the email and asks for any missing information or next steps.";
        };

        String userMsg = """
                From: %s <%s>
                Subject: %s

                Email summary (AI brief): %s

                Suggested action: %s

                %s
                """.formatted(
                email.getFromName(), email.getFromAddress(),
                email.getSubject(),
                email.getBrief(),
                email.getSuggestedAction(),
                toneInstruction
        );

        try {
            String reply = chatClient.prompt()
                    .system(SYSTEM)
                    .user(userMsg)
                    .call()
                    .content();
            return reply != null ? reply.trim() : "Could not draft a reply. Please try again.";
        } catch (Exception e) {
            log.error("Reply draft failed: {}", e.getMessage());
            return "Could not draft a reply: " + e.getMessage();
        }
    }
}
