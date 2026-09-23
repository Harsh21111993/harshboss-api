package com.harshboss.config;

import com.harshboss.ai.advisors.SecurityAdvisor;
import com.harshboss.ai.advisors.SemanticCacheAdvisor;
import com.harshboss.ai.advisors.TracingAdvisor;
import com.harshboss.ai.security.AuditLogService;
import com.harshboss.ai.security.PiiRedactor;
import com.harshboss.ai.security.PromptInjectionDetector;
import com.harshboss.service.UserService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Builds the shared {@link ChatClient} with the full advanced-advisor stack
 * wired in (Layer 5 of the architecture):
 *
 * <pre>
 *   SecurityAdvisor          → injection detection + PII redaction + audit log
 *   MessageChatMemoryAdvisor  → JDBC-backed conversation memory
 *   SemanticCacheAdvisor     → cache LLM responses by embedding similarity
 *   SafeGuardAdvisor         → content moderation blocklist
 *   TracingAdvisor           → LLMOps: log tokens + cost + latency per call
 * </pre>
 *
 * <p>The tools come from the MCP client (spring-ai-starter-mcp-client), which
 * connects to the standalone atlas-mcp-server (port 8081). The ChatClient uses
 * {@code .toolCallbacks(provider.getToolCallbacks())} to call remote tools
 * over the MCP protocol — no local @Tool methods in this app.</p>
 */
@Configuration
public class SpringAiConfig {

    private static final String DEFAULT_SYSTEM_MESSAGE = """
            You are Harsh-Boss, an executive-assistant AI integrated into a productivity workspace.
            Always be concise, professional, and helpful. When asked for JSON, return valid JSON only.
            """;

    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect.POSTGRES)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    /**
     * The ChatClient with the full advisor stack + MCP client tools.
     *
     * <p>The {@link ToolCallbackProvider} is the MCP client's bean (from
     * spring-ai-starter-mcp-client). It connects to atlas-mcp-server at
     * {@code spring.ai.mcp.client.sse.connections.atlas-mcp-server.url} and exposes the remote tools.</p>
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                  ChatMemory chatMemory,
                                  VectorStore vectorStore,
                                  JdbcTemplate jdbcTemplate,
                                  UserService userService,
                                  PromptInjectionDetector injectionDetector,
                                  PiiRedactor piiRedactor,
                                  AuditLogService auditLogService,
                                  ToolCallbackProvider atlasMcpToolCallbackProvider) {
        SecurityAdvisor securityAdvisor = new SecurityAdvisor(injectionDetector, piiRedactor, auditLogService);
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        SemanticCacheAdvisor cacheAdvisor = new SemanticCacheAdvisor(vectorStore, jdbcTemplate);
        TracingAdvisor tracingAdvisor = new TracingAdvisor(auditLogService, userService);

        SafeGuardAdvisor safeGuardAdvisor = new SafeGuardAdvisor(List.of(
                "password", "secret key", "API key", "credit card number", "SSN"
        ));

        // Safely get tool callbacks (may be empty if MCP server is down)
        ToolCallback[] toolCallbacks;
        try {
            toolCallbacks = atlasMcpToolCallbackProvider.getToolCallbacks();
        } catch (Exception e) {
            toolCallbacks = new ToolCallback[0];
        }

        return builder
                .defaultSystem(DEFAULT_SYSTEM_MESSAGE)
                .defaultAdvisors(
                        securityAdvisor,        // 1st: block injections + redact PII + audit
                        memoryAdvisor,          // 2nd: load conversation memory
                        cacheAdvisor,           // 3rd: semantic cache (skip LLM on similar Q)
                        safeGuardAdvisor,       // 4th: content moderation
                        tracingAdvisor          // 5th: trace tokens + cost + latency
                )
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }
}
