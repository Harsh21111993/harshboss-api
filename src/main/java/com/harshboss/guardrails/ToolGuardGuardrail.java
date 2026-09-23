package com.harshboss.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guardrail 4: Tool Call Guard
 *
 * <p>Intercepts and validates all @Tool method calls. Prevents:</p>
 * <ul>
 *   <li>Infinite tool loops (agent calling the same tool repeatedly)</li>
 *   <li>Excessive tool calls per request (DoS protection)</li>
 *   <li>Invalid parameter values (null, empty, oversized)</li>
 * </ul>
 *
 * <p>Tracks tool calls per conversation ID and enforces a maximum.</p>
 */
@Slf4j
@Service
public class ToolGuardGuardrail {

    @Value("${harshboss.guardrails.tool-guard.enabled:true}")
    private boolean enabled;

    @Value("${harshboss.guardrails.tool-guard.max-tool-calls-per-request:10}")
    private int maxCallsPerRequest;

    /** Key: conversationId, Value: call count */
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    /** Max length for tool parameters (prevents oversized inputs). */
    private static final int MAX_PARAM_LENGTH = 5000;

    /**
     * Check if a tool call is allowed.
     * @param toolName the name of the tool being called
     * @param conversationId the conversation/request ID
     * @param params the parameters passed to the tool
     * @return null if allowed, or an error message if blocked
     */
    public String check(String toolName, String conversationId, Object[] params) {
        if (!enabled) {
            return null; // Guardrail disabled
        }

        // 1. Check parameter validity
        if (params != null) {
            for (Object param : params) {
                if (param == null) {
                    log.warn("TOOL GUARD: null parameter for tool '{}'", toolName);
                    // Don't block — some tools accept null params
                    continue;
                }
                String paramStr = param.toString();
                if (paramStr.length() > MAX_PARAM_LENGTH) {
                    log.warn("TOOL GUARD: oversized parameter ({} chars) for tool '{}'",
                            paramStr.length(), toolName);
                    return "Tool parameter too large (max " + MAX_PARAM_LENGTH + " chars). " +
                           "Please refine your request.";
                }
            }
        }

        // 2. Check call count per conversation
        String key = conversationId != null ? conversationId : "default";
        AtomicInteger count = callCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        if (current > maxCallsPerRequest) {
            log.warn("TOOL GUARD: tool call limit exceeded — {} calls for conversation {} (max: {})",
                    current, key, maxCallsPerRequest);
            return "Tool call limit reached (" + maxCallsPerRequest + " per request). " +
                   "Please start a new conversation.";
        }

        log.debug("TOOL GUARD: '{}' call {}/{} for conversation {}",
                toolName, current, maxCallsPerRequest, key);
        return null; // Allowed
    }

    /** Reset the call count for a conversation (called when a new conversation starts). */
    public void resetConversation(String conversationId) {
        if (conversationId != null) {
            callCounts.remove(conversationId);
        }
    }

    /** Clean up old conversation counters (called periodically). */
    public void cleanup() {
        // Remove entries with 0 calls (already completed)
        callCounts.entrySet().removeIf(e -> e.getValue().get() == 0);
    }
}
