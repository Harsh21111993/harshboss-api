package com.harshboss.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Guardrail 3: Topic Restriction
 *
 * <p>Ensures the AI agent only discusses work/productivity-related topics.
 * Prevents off-topic conversations (politics, personal advice, entertainment,
 * etc.) that waste tokens and could damage the product's reputation.</p>
 *
 * <p>Uses a keyword-based fast filter (no LLM call needed — saves cost).</p>
 */
@Slf4j
@Service
public class TopicRestrictionGuardrail {

    @Value("${harshboss.guardrails.topic-restriction.enabled:true}")
    private boolean enabled;

    @Value("${harshboss.guardrails.topic-restriction.allowed-topics:email,calendar,meetings,tasks,jobs,career,productivity,work,schedule}")
    private String allowedTopicsStr;

    /** Off-topic keywords that trigger a redirect. */
    private static final List<String> OFF_TOPIC_KEYWORDS = List.of(
            "politics", "election", "vote for", "democrat", "republican",
            "religion", "god says", "pray",
            "gambling", "casino", "lottery ticket", "bet on",
            "illegal", "drug", "weapon", "hack into", "steal",
            "dating", "relationship advice", "marriage",
            "entertainment", "movie recommendation", "netflix", "video game",
            "medical advice", "diagnosis", "prescribe",
            "legal advice", "lawsuit", "sue someone",
            "financial advice", "stock tip", "invest in", "crypto tip"
    );

    /**
     * Check if the user's message is on-topic.
     * @return null if on-topic, or a redirect message if off-topic.
     */
    public String check(String userMessage) {
        if (!enabled || userMessage == null || userMessage.isBlank()) {
            return null;
        }

        String lower = userMessage.toLowerCase();

        for (String keyword : OFF_TOPIC_KEYWORDS) {
            if (lower.contains(keyword)) {
                log.info("TOPIC RESTRICTION: off-topic keyword '{}' detected — redirecting", keyword);
                return "I'm Harsh-Boss, your work productivity assistant. I can help with emails, " +
                       "calendar, meetings, tasks, and job search — but I'm not able to discuss " +
                       "that topic. Is there something work-related I can help you with?";
            }
        }

        return null; // On-topic
    }

    /**
     * Check if the AI response stayed on-topic (post-generation check).
     * @return the original response if on-topic, or a sanitized version if it drifted.
     */
    public String checkResponse(String aiResponse) {
        if (!enabled || aiResponse == null) {
            return aiResponse;
        }
        // For now, we trust the system prompt to keep responses on-topic.
        // A full implementation would use an LLM classifier here.
        return aiResponse;
    }
}
