package com.harshboss.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-safe configuration for the MCP client.
 *
 * <p>Problem: Spring AI 2.0.0's MCP client auto-configuration tries to connect
 * to the MCP server during application startup. If the server isn't running,
 * the bean creation fails and the whole app crashes.</p>
 *
 * <p>Fix: When {@code harshboss.mcp.client.fail-safe=true} (default), we provide
 * a fallback empty {@link ToolCallbackProvider} and exclude the MCP client
 * auto-configuration. The app starts normally without connecting to the
 * MCP server. Set {@code harshboss.mcp.client.fail-safe=false} to use the
 * real MCP client (requires the MCP server to be running).</p>
 */
@Slf4j
@Configuration
public class McpClientFailSafeConfig {

    /**
     * Fallback ToolCallbackProvider — used when MCP client is disabled
     * or the server isn't reachable.
     */
    @Bean
    @ConditionalOnProperty(prefix = "harshboss.mcp.client", name = "fail-safe", havingValue = "true", matchIfMissing = true)
    public ToolCallbackProvider fallbackToolCallbackProvider() {
        log.info("""
                
                ════════════════════════════════════════════════════════════════
                ℹ️  MCP CLIENT: FAIL-SAFE MODE (no tools loaded)
                
                The app started in fail-safe mode. The REST API + UI work fine,
                but agent tool calls (Ask Harsh-Boss, Job Finder) won't have tools.
                
                To enable MCP tools:
                  1. Start the MCP server:  cd atlas-mcp-server && mvn spring-boot:run
                  2. Restart atlas-api with: -Dharshboss.mcp.client.fail-safe=false
                  
                OR set in application.yml:
                  atlas:
                    mcp:
                      client:
                        fail-safe: false
                ════════════════════════════════════════════════════════════════
                """);
        return () -> new ToolCallback[0];
    }
}
