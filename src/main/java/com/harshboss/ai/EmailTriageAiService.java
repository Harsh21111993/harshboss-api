package com.harshboss.ai;

import com.harshboss.dto.EmailAnalysisDto;
import com.harshboss.entity.Email;
import com.harshboss.entity.enums.EmailLabel;
import com.harshboss.entity.enums.Importance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring AI triage of a single email. Uses structured output via
 * {@code ChatClient.entity(EmailAnalysisDto.class)} so Spring AI handles the
 * JSON-schema prompt and Jackson deserialization automatically.
 *
 * <p>The system prompt (loaded from {@code prompts/email-triage.st}) explicitly
 * instructs the model to score TRUE importance regardless of folder — this is
 * what surfaces the "buried in spam" partnership follow-up.</p>
 *
 * <p>All failures are caught and a safe {@link EmailLabel#FYI} fallback is
 * returned so a single bad email never 500s the API.</p>
 */
@Slf4j
@Service
public class EmailTriageAiService {

    private final ChatClient chatClient;
    private final String systemPrompt;

    public EmailTriageAiService(ChatClient chatClient,
                                @Value("classpath:prompts/email-triage.st") Resource promptResource)
            throws IOException {
        this.chatClient = chatClient;
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Run AI triage on one email and return the structured result.
     * On any AI failure, returns a conservative FYI fallback.
     */
    public EmailAnalysisDto analyze(Email email) {
        if (email == null || email.getId() == null) {
            return fallback(email, "null email");
        }
        try {
            String userMsg = """
                    Sender: %s <%s>
                    Subject: %s
                    Folder: %s
                    Received at: %s

                    Body:
                    %s
                    """.formatted(
                    email.getFromName(),
                    email.getFromAddress(),
                    email.getSubject(),
                    email.getFolder(),
                    email.getReceivedAt(),
                    email.getBody()
            );

            EmailAnalysisDto result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMsg)
                    .call()
                    .entity(EmailAnalysisDto.class);

            if (result == null) {
                return fallback(email, "null AI response");
            }
            return normalize(result);
        } catch (Exception e) {
            log.warn("AI triage failed for email {} ({}): {}", email.getId(), email.getSubject(), e.getMessage());
            return fallback(email, e.getMessage());
        }
    }

    /** Make sure list fields are never null and score is in range. */
    private EmailAnalysisDto normalize(EmailAnalysisDto dto) {
        List<String> actionItems = dto.actionItems() == null ? List.of() : dto.actionItems();
        List<String> keyDates    = dto.keyDates()    == null ? List.of() : dto.keyDates();
        int score = Math.max(0, Math.min(100, dto.score()));
        return new EmailAnalysisDto(
                dto.brief() == null ? "" : dto.brief(),
                dto.label() == null ? EmailLabel.FYI : dto.label(),
                dto.importance() == null ? Importance.LOW : dto.importance(),
                score,
                dto.reason() == null ? "" : dto.reason(),
                dto.suggestedAction() == null ? "" : dto.suggestedAction(),
                actionItems,
                keyDates
        );
    }

    private EmailAnalysisDto fallback(Email email, String reason) {
        return new EmailAnalysisDto(
                "AI triage unavailable — please review this email manually.",
                EmailLabel.FYI,
                Importance.LOW,
                0,
                "AI service did not return a result (" + (reason == null ? "unknown" : reason) + ").",
                "Open and read the email to decide next steps.",
                List.of(),
                List.of()
        );
    }
}
