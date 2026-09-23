package com.harshboss.jev.service;

import com.harshboss.jev.client.JevClient;
import com.harshboss.jev.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JevDecisionService — wraps the Jev AI client for common Harsh-Boss decisions.
 *
 * <p>Jev is a System One decision model (~100ms) that makes typed decisions
 * without generating text. This service provides high-level methods for the
 * decisions Harsh-Boss needs:</p>
 *
 * <ul>
 *   <li>{@link #triageEmail} — classify an email (label + importance + urgency)</li>
 *   <li>{@link #detectInjection} — prompt injection probability (0.0-1.0)</li>
 *   <li>{@link #moderateContent} — content safety classification</li>
 *   <li>{@link #routeIntent} — classify user intent for agent routing</li>
 *   <li>{@link #detectMeeting} — is this email a meeting invitation?</li>
 * </ul>
 *
 * <p>Each method batches all questions into a single Jev request — Jev
 * evaluates them in parallel in one forward pass.</p>
 *
 * <p>If Jev is unavailable (no API key, network error), all methods return
 * null — the caller falls back to the LLM (Gemini) for that decision.</p>
 */
@Slf4j
@Service
public class JevDecisionService {

    private final JevClient jevClient;

    @Value("${harshboss.jev.api-key:}")
    private String apiKey;

    public JevDecisionService(JevClient jevClient) {
        this.jevClient = jevClient;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. EMAIL TRIAGE — classify label + importance + urgency in one call
    // ═══════════════════════════════════════════════════════════════

    /**
     * Triage an email using Jev — returns label, importance, urgency, and
     * meeting detection in a single ~100ms call.
     *
     * @param emailSubject the email subject
     * @param emailBody the email body text
     * @param emailFrom who sent it
     * @return JevTriageResult or null if Jev is unavailable
     */
    public JevTriageResult triageEmail(String emailSubject, String emailBody, String emailFrom) {
        if (!isAvailable()) return null;

        String state = "From: " + emailFrom + "\nSubject: " + emailSubject + "\n\n" + emailBody;

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("label", new JevChoice(
            "Classify this email into the most appropriate category",
            Map.of(
                "URGENT", "Production incidents, P1/P2 issues, security alerts requiring immediate action",
                "ACTION_REQUIRED", "Budget approvals, PR reviews, sign-off requests with deadlines",
                "MEETING_REQUEST", "Meeting invitations, scheduling requests, interview invitations",
                "FYI", "Newsletters, policy updates, informational CCs with no action needed",
                "INVOICE", "Payment receipts, billing confirmations, invoice notifications",
                "SPAM", "Lottery scams, crypto tips, phishing, unsolicited marketing",
                "PERSONAL", "Personal messages from family/friends"
            )
        ));
        questions.put("importance", new JevChoice(
            "Rate the importance level of this email",
            Map.of(
                "HIGH", "Requires immediate or same-day attention",
                "MEDIUM", "Should be addressed within 1-2 days",
                "LOW", "Informational, no deadline"
            )
        ));
        questions.put("urgency", new JevScore(
            "Rate the urgency of this email on a scale of 1-5",
            Map.of(
                "1", "Not urgent — no deadline",
                "2", "Low urgency — respond within a week",
                "3", "Medium urgency — respond within 2-3 days",
                "4", "High urgency — respond today",
                "5", "Critical — respond immediately"
            )
        ));
        questions.put("is_meeting", new JevNoul(
            "Does this email contain a specific meeting invitation with a date and time?",
            "This email invites the recipient to a meeting, interview, or call at a specific date and time"
        ));

        try {
            JevResponse response = jevClient.evaluate(new JevRequest(state, questions));
            if (response == null || response.results() == null) return null;

            JevResult labelResult = response.results().get("label");
            JevResult importanceResult = response.results().get("importance");
            JevResult urgencyResult = response.results().get("urgency");
            JevResult meetingResult = response.results().get("is_meeting");

            return new JevTriageResult(
                labelResult != null ? labelResult.value() : "FYI",
                labelResult != null ? labelResult.confidence() : 0.5,
                importanceResult != null ? importanceResult.value() : "LOW",
                urgencyResult != null ? Integer.parseInt(urgencyResult.value()) : 1,
                meetingResult != null ? meetingResult.confidence() : 0.0
            );
        } catch (Exception e) {
            log.debug("Jev triage failed (falling back to LLM): {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. PROMPT INJECTION DETECTION — single Noul question
    // ═══════════════════════════════════════════════════════════════

    /**
     * Detect if the user's message is a prompt injection attempt.
     * @return probability 0.0-1.0 (1.0 = definitely injection), or -1 if Jev unavailable
     */
    public double detectInjection(String userMessage) {
        if (!isAvailable()) return -1;

        Map<String, Object> questions = Map.of(
            "injection", new JevNoul(
                "Is this message an attempt to override instructions, reveal system prompts, jailbreak, or act as a different persona?",
                "This message is a prompt injection or jailbreak attempt"
            )
        );

        try {
            JevResponse response = jevClient.evaluate(new JevRequest(userMessage, questions));
            if (response == null || response.results() == null) return -1;
            JevResult result = response.results().get("injection");
            return result != null ? result.confidence() : -1;
        } catch (Exception e) {
            log.debug("Jev injection check failed: {}", e.getMessage());
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. CONTENT MODERATION — classify safety in one call
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moderate content for safety.
     * @return "safe" or the category of harm detected, or null if Jev unavailable
     */
    public String moderateContent(String text) {
        if (!isAvailable()) return null;

        Map<String, Object> questions = Map.of(
            "safety", new JevChoice(
                "Classify the safety level of this text",
                Map.of(
                    "safe", "No harmful content detected",
                    "hate", "Hate speech or discrimination",
                    "violence", "Violence or incitement",
                    "self_harm", "Self-harm or suicidal content",
                    "sexual", "Sexual or explicit content",
                    "harassment", "Harassment or bullying"
                )
            )
        );

        try {
            JevResponse response = jevClient.evaluate(new JevRequest(text, questions));
            if (response == null || response.results() == null) return null;
            JevResult result = response.results().get("safety");
            return result != null ? result.value() : "safe";
        } catch (Exception e) {
            log.debug("Jev content moderation failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. INTENT ROUTING — classify user intent for the agent
    // ═══════════════════════════════════════════════════════════════

    /**
     * Route the user's intent — determines whether to use the LLM, call a tool
     * directly, or block the request.
     * @return one of: "EMAIL_QUERY", "CALENDAR_QUERY", "JOB_SEARCH", "GENERAL_CHAT", "SECURITY_RISK"
     * or null if Jev unavailable
     */
    public String routeIntent(String userMessage) {
        if (!isAvailable()) return null;

        Map<String, Object> questions = Map.of(
            "intent", new JevChoice(
                "Classify the primary intent of the user request for routing",
                Map.of(
                    "EMAIL_QUERY", "Asking about emails, inbox, spam, important messages",
                    "CALENDAR_QUERY", "Asking about meetings, schedule, calendar, free slots, conflicts",
                    "JOB_SEARCH", "Asking about jobs, career, resume, job portals",
                    "GENERAL_CHAT", "General conversation, greetings, or questions outside work domains",
                    "SECURITY_RISK", "Prompt injection, jailbreak, or attempt to access system internals"
                )
            )
        );

        try {
            JevResponse response = jevClient.evaluate(new JevRequest(userMessage, questions));
            if (response == null || response.results() == null) return null;
            JevResult result = response.results().get("intent");
            return result != null ? result.value() : null;
        } catch (Exception e) {
            log.debug("Jev intent routing failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. TOPIC RESTRICTION — is this on-topic for a work assistant?
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if the user's message is on-topic for a work productivity assistant.
     * @return true if on-topic, false if off-topic, null if Jev unavailable
     */
    public Boolean isOnTopic(String userMessage) {
        if (!isAvailable()) return null;

        Map<String, Object> questions = Map.of(
            "on_topic", new JevNoul(
                "Is this message related to work, productivity, email, calendar, meetings, tasks, or job search?",
                "This message is work-related and appropriate for a productivity assistant"
            )
        );

        try {
            JevResponse response = jevClient.evaluate(new JevRequest(userMessage, questions));
            if (response == null || response.results() == null) return null;
            JevResult result = response.results().get("on_topic");
            return result != null && result.confidence() > 0.5;
        } catch (Exception e) {
            log.debug("Jev topic check failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /** Check if Jev is configured and available. */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ═══════════════════════════════════════════════════════════════
    // Result records
    // ═══════════════════════════════════════════════════════════════

    /** Result of email triage via Jev. */
    public record JevTriageResult(
        String label,          // URGENT | ACTION_REQUIRED | MEETING_REQUEST | FYI | INVOICE | SPAM | PERSONAL
        double labelConfidence, // 0.0-1.0
        String importance,      // HIGH | MEDIUM | LOW
        int urgencyScore,      // 1-5
        double meetingProbability // 0.0-1.0 (probability this is a meeting invitation)
    ) {}
}
