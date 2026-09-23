package com.harshboss.ai.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists every AI interaction to the {@code audit_log} table (LLMOps).
 *
 * <p>For each request-response pair, two rows are written:
 * <ul>
 *   <li>direction=REQUEST (the user's message)</li>
 *   <li>direction=RESPONSE (the AI's answer, with token count + cost)</li>
 * </ul>
 * Both share the same {@code interactionId} so they can be correlated.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public UUID logRequest(UUID userId, String content, String promptHash) {
        UUID interactionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO audit_log (interaction_id, user_id, direction, content, prompt_hash, created_at) " +
                "VALUES (?, ?, 'REQUEST', ?, ?, ?)",
                interactionId, userId, content, promptHash, Instant.now()
        );
        return interactionId;
    }

    public void logResponse(UUID interactionId, UUID userId, String content,
                            String model, Integer promptTokens, Integer completionTokens,
                            Integer totalTokens, Double costUsd, Long latencyMs,
                            boolean flagged, String flagReason) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (interaction_id, user_id, direction, content, model, " +
                "prompt_tokens, completion_tokens, total_tokens, estimated_cost_usd, latency_ms, " +
                "flagged, flag_reason, created_at) " +
                "VALUES (?, ?, 'RESPONSE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                interactionId, userId, content, model,
                promptTokens, completionTokens, totalTokens, costUsd, latencyMs,
                flagged, flagReason, Instant.now()
        );
    }

    /** SHA-256 hash of the prompt (for dedup + semantic cache lookup). */
    public static String hash(String text) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
