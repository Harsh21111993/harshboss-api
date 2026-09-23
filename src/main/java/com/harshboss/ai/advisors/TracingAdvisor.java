package com.harshboss.ai.advisors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientRequestAdvisor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;

import com.harshboss.ai.security.AuditLogService;
import com.harshboss.service.UserService;

import java.util.UUID;

/**
 * LLMOps Tracing Advisor — logs every ChatClient call to the audit_log table
 * with token count, estimated cost, and latency.
 *
 * <p>Implements both {@link ChatClientRequestAdvisor} (to capture the start
 * time) and {@link ChatClientResponseAdvisor} (to log the response metrics).</p>
 *
 * <p>Spring AI 2.0.0 API: uses {@code ChatClientRequest} / {@code ChatClientResponse}
 * instead of the old {@code AdvisedRequest} / {@code AdvisedResponse}.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class TracingAdvisor implements ChatClientRequestAdvisor, ChatClientResponseAdvisor {

    private final AuditLogService auditLogService;
    private final UserService userService;

    @Override
    public ChatClientRequest adviseRequest(ChatClientRequest request) {
        request.context().put("__trace_start", System.currentTimeMillis());
        return request;
    }

    @Override
    public ChatClientResponse adviseResponse(ChatClientResponse response) {
        try {
            Long start = (Long) response.context().get("__trace_start");
            long latencyMs = start != null ? System.currentTimeMillis() - start : 0;

            ChatResponse chatResponse = response.chatResponse();
            Usage usage = chatResponse != null && chatResponse.getMetadata() != null
                    ? chatResponse.getMetadata().getUsage() : null;

            Integer promptTokens = usage != null && usage.getPromptTokens() != null
                    ? usage.getPromptTokens().intValue() : null;
            Integer completionTokens = usage != null && usage.getCompletionTokens() != null
                    ? usage.getCompletionTokens().intValue() : null;
            Integer totalTokens = usage != null && usage.getTotalTokens() != null
                    ? usage.getTotalTokens().intValue() : null;

            // Rough cost estimate for Gemini 2.0 Flash: $0.10/1M input + $0.40/1M output
            double costUsd = 0;
            if (promptTokens != null) costUsd += promptTokens * 0.0000001;
            if (completionTokens != null) costUsd += completionTokens * 0.0000004;

            UUID userId = safeUserId();
            UUID interactionId = (UUID) response.context().getOrDefault("__interaction_id", UUID.randomUUID());
            String model = chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getModel() != null
                    ? chatResponse.getMetadata().getModel() : "gemini";

            String responseText = chatResponse != null && chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null
                    && chatResponse.getResult().getOutput().getText() != null
                    ? chatResponse.getResult().getOutput().getText() : "";

            auditLogService.logResponse(interactionId, userId, responseText, model,
                    promptTokens, completionTokens, totalTokens, costUsd, latencyMs,
                    false, null);

            log.debug("AI trace: model={}, tokens={}/{}/{}, cost=${}, latency={}ms",
                    model, promptTokens, completionTokens, totalTokens,
                    String.format("%.4f", costUsd), latencyMs);
        } catch (Exception e) {
            log.warn("Tracing advisor failed (non-blocking): {}", e.getMessage());
        }
        return response;
    }

    private UUID safeUserId() {
        try { return userService.requireCurrentUserId(); }
        catch (Exception e) { return null; }
    }
}
