package com.harshboss.ai.advisors;

import com.harshboss.ai.security.AuditLogService;
import com.harshboss.ai.security.PiiRedactor;
import com.harshboss.ai.security.PromptInjectionDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientRequestAdvisor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientResponseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.UUID;

/**
 * Security Advisor — the OWASP LLM Top 10 mitigation layer.
 *
 * <p>Runs before any other advisor and the LLM call itself. Chain:
 * <ol>
 *   <li>PromptInjectionDetector — block injection attempts (LLM01)</li>
 *   <li>PiiRedactor — strip emails/phones/SSNs (LLM02)</li>
 *   <li>AuditLogService — persist the request for traceability (LLM07)</li>
 * </ol>
 *
 * <p>Spring AI 2.0.0 API: uses {@code ChatClientRequest} / {@code ChatClientResponse}.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class SecurityAdvisor implements ChatClientRequestAdvisor, ChatClientResponseAdvisor {

    private static final String REFUSAL = """
            I can't process that request — it appears to contain an attempt to
            override my instructions. If you believe this is a mistake, please
            rephrase your question.
            """;

    private final PromptInjectionDetector injectionDetector;
    private final PiiRedactor piiRedactor;
    private final AuditLogService auditLogService;

    @Override
    public ChatClientRequest adviseRequest(ChatClientRequest request) {
        String userText = extractUserText(request);

        // 1. Prompt injection detection
        if (userText != null && !userText.isBlank()) {
            PromptInjectionDetector.Verdict verdict = injectionDetector.check(userText);
            if (verdict.blocked()) {
                log.warn("Blocked prompt injection: score={}, reason={}", verdict.score(), verdict.reason());
                UUID interactionId = auditLogService.logRequest(null, userText, AuditLogService.hash(userText));
                auditLogService.logResponse(interactionId, null, REFUSAL, "security-block", 0, 0, 0, 0.0, 0L, true, verdict.reason());
                request.context().put("__blocked", true);
                request.context().put("__refusal", REFUSAL);
                return request;
            }
        }

        // 2. PII redaction
        if (userText != null && !userText.isBlank()) {
            String redacted = piiRedactor.redact(userText);
            if (!redacted.equals(userText)) {
                // Replace the last user message with the redacted version
                request.messages().replaceAll(m ->
                    m instanceof UserMessage um && um.getText().equals(userText)
                        ? new UserMessage(redacted) : m);
            }
        }

        // 3. Audit log the request
        try {
            UUID interactionId = auditLogService.logRequest(null, userText, AuditLogService.hash(userText));
            request.context().put("__interaction_id", interactionId);
        } catch (Exception e) {
            log.debug("Audit log failed (non-blocking): {}", e.getMessage());
        }

        return request;
    }

    @Override
    public ChatClientResponse adviseResponse(ChatClientResponse response) {
        // If the request was blocked, return the refusal
        Object blocked = response.context().get("__blocked");
        if (Boolean.TRUE.equals(blocked)) {
            return response;
        }

        // Restore PII in the response
        try {
            String responseText = response.chatResponse() != null
                    && response.chatResponse().getResult() != null
                    && response.chatResponse().getResult().getOutput() != null
                    ? response.chatResponse().getResult().getOutput().getText() : null;
            if (responseText != null) {
                piiRedactor.restore(responseText);
            }
        } catch (Exception e) {
            log.debug("PII restore failed (non-blocking): {}", e.getMessage());
        }
        return response;
    }

    private String extractUserText(ChatClientRequest request) {
        if (request.messages() == null) return null;
        return request.messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> m.getText())
                .reduce("", (a, b) -> a + " " + b)
                .trim();
    }
}
