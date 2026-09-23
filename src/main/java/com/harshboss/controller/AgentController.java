package com.harshboss.controller;
import com.harshboss.aspect.annotation.RateLimit;

import com.harshboss.ai.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent chat endpoint — the conversational AI interface.
 *
 * <p>POST /api/agent/chat  body: { "message": "find my buried important emails" }
 *  → { "response": "I found 1 important email buried in your spam folder: ..." }</p>
 *
 * <p>The agent autonomously calls tools (fetchEmails, findBuriedImportantEmails,
 * checkCalendarConflict, findFreeSlots) as needed to answer the question.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    @RateLimit(maxCalls = 20, windowSeconds = 60)
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        log.info("POST /api/agent/chat: {}", message.length() > 80 ? message.substring(0, 80) + "…" : message);
        String response = agentService.chat(message);
        return ResponseEntity.ok(Map.of("response", response));
    }

    @PostMapping("/clear")
    public ResponseEntity<Void> clear() {
        agentService.clearHistory();
        return ResponseEntity.noContent().build();
    }
}
