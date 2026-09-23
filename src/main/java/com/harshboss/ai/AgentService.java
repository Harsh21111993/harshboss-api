package com.harshboss.ai;
import com.harshboss.guardrails.Guardrails;
import com.harshboss.aspect.annotation.RetryOnFailure;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.jev.service.JevDecisionService;

import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * The Spring AI ChatClient agent — the "brain" that reasons about user
 * requests and autonomously calls tools.
 *
 * <p>The tools are <b>remote</b> — they live in the standalone
 * {@code atlas-mcp-server} (port 8081) and are called over the MCP protocol.
 * The ChatClient's {@code defaultToolCallbacks} (wired in SpringAiConfig)
 * contain the MCP client's tool callbacks, so the agent calls them
 * transparently — it doesn't know they're remote.</p>
 *
 * <p>This separation means:
 * <ul>
 *   <li>You can update tools in atlas-mcp-server without restarting atlas-api</li>
 *   <li>Multiple agents (Spring AI, LangChain4j, Google ADK, Claude) can
 *       call the same tools</li>
 *   <li>The tools can be scaled independently of the REST API</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatClient chatClient;
    private final UserService userService;
    private final JevDecisionService jevDecisionService;

    private static final String SYSTEM_PROMPT = """
            You are Harsh-Boss, an AI productivity assistant for a busy engineering manager.
            You have access to tools that read the user's REAL email inbox, spam folder,
            and calendar. Use the tools to answer the user's questions — never guess.

            Rules:
            1. ALWAYS call a tool before answering if the question involves emails or calendar.
               Never invent email content or meeting times.
            2. When the user asks about "important" emails, call findImportantEmails first.
            3. When the user mentions something "buried in spam", call findBuriedImportantEmails.
            4. When proposing a meeting, ALWAYS call checkCalendarConflict first, then
               call findFreeSlots to suggest alternatives if there's a conflict.
            5. Be concise. The user is busy. Lead with the answer, then give details.
            6. Reference specific emails by sender + subject when relevant.
            7. If a tool fails (e.g. no emails synced yet, or MCP server is down),
               tell the user clearly what to do.

            You are talking to: %s (%s).
            """;

    /**
     * Send a message to the agent and get a response. The agent may call
     * multiple remote tools (via MCP) autonomously before answering.
     */
    @RetryOnFailure(maxAttempts = 2, delayMs = 2000)
    @MeasurePerformance(warnThresholdMs = 30000)
    @AuditAction(action = "AGENT_CHAT", description = "AI agent chat with MCP tools")
    @Guardrails
    public String chat(String userMessage) {
        // ── JEV PRE-ROUTING (System One decision gate, ~100ms) ──
        // If Jev is available, classify intent before calling the expensive LLM.
        if (jevDecisionService != null && jevDecisionService.isAvailable()) {
            String intent = jevDecisionService.routeIntent(userMessage);
            if (intent != null) {
                log.info("Jev routed intent: {} for message: '{}'", intent,
                        userMessage.substring(0, Math.min(60, userMessage.length())));

                // Fast-path: security risk → block immediately (no LLM call)
                if ("SECURITY_RISK".equals(intent)) {
                    return "I can't process that request — it appears to contain an attempt to "
                         + "override my instructions. If you believe this is a mistake, please "
                         + "rephrase your question.";
                }

                // Fast-path: greeting → instant response (no LLM call, no tools)
                if ("GENERAL_CHAT".equals(intent)) {
                    String lower = userMessage.toLowerCase().trim();
                    if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") ||
                        lower.startsWith("how are you")) {
                        return "Hello! I'm Harsh-Boss, your AI work assistant. "
                             + "I can help with emails, calendar, meetings, tasks, and job search. "
                             + "What would you like to do?";
                    }
                }
                // For EMAIL_QUERY, CALENDAR_QUERY, JOB_SEARCH → fall through to LLM + tools
            }
        }

        // ── LLM PATH (Gemini + MCP tools) ──
        String userName = "User";
        String userEmail = "unknown";
        try {
            var user = userService.getCurrentUser();
            userName = user.fullName();
            userEmail = user.email();
        } catch (Exception e) {
            log.debug("Could not resolve current user for agent (using defaults): {}", e.getMessage());
        }

        String systemMessage = String.format(SYSTEM_PROMPT, userName, userEmail);

        try {
            // Use a conversation ID so the MessageChatMemoryAdvisor can persist
            // and retrieve the conversation history from the JDBC repository.
            String conversationId = "user-" + userService.requireCurrentUserId();

            String response = chatClient.prompt()
                    .system(systemMessage)
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    // Tools are wired via defaultToolCallbacks in SpringAiConfig
                    // (the MCP client's ToolCallbackProvider). No .tools() needed here.
                    .call()
                    .content();

            log.info("Agent responded to: '{}' ({} chars)", truncate(userMessage, 60),
                    response != null ? response.length() : 0);
            return response != null ? response : "I couldn't process that request. Please try again.";
        } catch (Exception e) {
            log.error("Agent chat failed: {}", e.getMessage(), e);
            return "I ran into an issue processing that: " + e.getMessage()
                    + "\n\nTry rephrasing, or make sure the MCP server is running (port 8081) "
                    + "and you have synced some emails first (Profile → Sync emails).";
        }
    }

    /** Clear the conversation history (start fresh). */
    public void clearHistory() {
        // Memory is JDBC-backed; clearing happens via the memory repository.
        // For now this is a no-op — a future enhancement would clear the
        // SPRING_AI_CHAT_MEMORY rows for the current conversation ID.
        log.info("Clear conversation history requested");
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
