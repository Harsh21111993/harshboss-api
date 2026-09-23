package com.harshboss.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Guardrail 1: Content Moderation
 *
 * <p>Uses a fast LLM classifier to detect harmful content in user inputs
 * and AI outputs. Blocks content that scores above the threshold for:
 * <ul>
 *   <li>Hate speech</li>
 *   <li>Violence / incitement</li>
 *   <li>Self-harm</li>
 *   <li>Sexual content</li>
 *   <li>Harassment</li>
 * </ul>
 *
 * <p>OWASP LLM Top 10 — LLM08: Insecure Output Handling (prevents harmful outputs).</p>
 */
@Slf4j
@Service
public class ContentModerationGuardrail {

    private final ChatClient chatClient;

    @Value("${harshboss.guardrails.content-moderation.enabled:true}")
    private boolean enabled;

    @Value("${harshboss.guardrails.content-moderation.threshold:0.7}")
    private double threshold;

    private static final String SYSTEM = """
            You are a content safety classifier. Analyze the text and determine if it contains
            harmful content in any of these categories: hate, violence, self-harm, sexual, harassment.

            Respond with valid JSON only:
            {"safe": true/false, "score": 0.0-1.0, "category": "none|hate|violence|self_harm|sexual|harassment", "reason": "one sentence"}

            0.0 = completely safe, 1.0 = clearly harmful.
            """;

    private final com.harshboss.jev.service.JevDecisionService jevDecisionService;

    public ContentModerationGuardrail(ChatClient chatClient,
                                       com.harshboss.jev.service.JevDecisionService jevDecisionService) {
        this.chatClient = chatClient;
        this.jevDecisionService = jevDecisionService;
    }

    /**
     * Check if the text is safe.
     * @return null if safe, or a rejection message if harmful.
     */
    public String check(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return null; // Guardrail disabled or empty text → allow
        }

        // ── JEV FAST PATH (~100ms) ──
        if (jevDecisionService != null && jevDecisionService.isAvailable()) {
            String category = jevDecisionService.moderateContent(text);
            if (category != null && !"safe".equals(category)) {
                log.warn("Jev content moderation BLOCKED: category={}", category);
                return "Content blocked by safety filter (category: " + category + "). "
                     + "Please rephrase your request.";
            }
            if ("safe".equals(category)) {
                return null; // Safe — no need for LLM check
            }
        }

        // ── LLM FALLBACK (~2-5s) ──
        try {
            String json = chatClient.prompt()
                    .system(SYSTEM)
                    .user(text)
                    .call()
                    .content();

            double score = parseScore(json);
            boolean safe = parseSafe(json);
            String category = parseCategory(json);

            if (!safe || score >= threshold) {
                log.warn("CONTENT MODERATION BLOCKED: category={}, score={}, text='{}'",
                        category, score, text.substring(0, Math.min(80, text.length())));
                return "Content blocked by safety filter (category: " + category + ", score: " + score + "). " +
                       "Please rephrase your request.";
            }

            return null; // Safe
        } catch (Exception e) {
            log.debug("Content moderation check failed (allowing): {}", e.getMessage());
            return null; // Fail open — don't block on classifier errors
        }
    }

    private double parseScore(String json) {
        if (json == null) return 0.0;
        int i = json.indexOf("\"score\"");
        if (i < 0) return 0.0;
        int colon = json.indexOf(':', i);
        int comma = json.indexOf(',', colon);
        String num = json.substring(colon + 1, comma > 0 ? comma : json.length()).replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(num); } catch (Exception e) { return 0.0; }
    }

    private boolean parseSafe(String json) {
        if (json == null) return true;
        return json.contains("\"safe\": true") || json.contains("\"safe\":true");
    }

    private String parseCategory(String json) {
        if (json == null) return "none";
        int i = json.indexOf("\"category\"");
        if (i < 0) return "none";
        int colon = json.indexOf(':', i);
        int quote = json.indexOf('"', colon);
        int end = json.indexOf('"', quote + 1);
        if (quote < 0 || end < 0) return "none";
        return json.substring(quote + 1, end);
    }
}
