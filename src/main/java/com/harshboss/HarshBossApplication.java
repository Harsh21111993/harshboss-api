package com.harshboss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Harsh-Boss API — Spring Boot 4.1 + Spring AI 2.0 + Google Gemini backend.
 *
 * <p>MCP CLIENT FAIL-SAFE: By default, the MCP client auto-configuration is
 * DISABLED so the app can start without the MCP server running. This means
 * agent tool calls won't work until you start the MCP server and restart.</p>
 *
 * <p>To enable the MCP client (requires the MCP server running on :8081):
 * <pre>
 *   # Option 1: JVM arg
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--spring.autoconfigure.exclude=
 *
 *   # Option 2: environment variable
 *   unset SPRING_AUTOCONFIGURE_EXCLUDE
 * </pre>
 * </p>
 */
@Slf4j
@SpringBootApplication(exclude = {
    // Disable MCP client auto-config by default — the app starts without it.
    // When you want MCP tools, set: -Dspring.autoconfigure.exclude= (empty)
    // OR remove this exclude annotation.
    org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration.class,
    org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration.class
})
@EnableScheduling
@EnableAspectJAutoProxy
public class HarshBossApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarshBossApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupBanner() {
        return args -> {
            log.info("""

                ════════════════════════════════════════════════════════════════
                Harsh-Boss API started on port 8080
                
                MCP client: DISABLED (fail-safe mode)
                → REST API + UI work fine
                → Agent tools (Ask Harsh-Boss, Job Finder) need the MCP server
                
                To enable MCP tools:
                  1. Start MCP server: cd atlas-mcp-server && mvn spring-boot:run
                  2. Remove the @exclude from HarshBossApplication.java
                  3. Restart atlas-api
                ════════════════════════════════════════════════════════════════
                """);
        };
    }
}
