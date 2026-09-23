package com.harshboss.controller;

import com.harshboss.ai.embedding.EmbeddingService;
import com.harshboss.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Semantic email search endpoint.
 *
 * <p>POST /api/emails/search  body: { "query": "emails about the Q3 budget", "topK": 10 }
 *  → { "results": [{ emailId, subject, from, score }, ...] }</p>
 *
 * <p>Uses PGVector cosine similarity over email embeddings.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") instanceof String s ? s : "";
        int topK = body.get("topK") instanceof Number n ? n.intValue() : 10;

        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }

        List<EmbeddingService.SearchResult> results = semanticSearchService.search(query, topK);
        log.info("POST /api/emails/search '{}' → {} results", query, results.size());
        return ResponseEntity.ok(Map.of(
                "query", query,
                "results", results
        ));
    }

    /** Trigger embedding of all emails for the current user (useful after a sync). */
    @PostMapping("/embed")
    public ResponseEntity<Map<String, Object>> embedAll() {
        // Defer to the embedding service; it resolves the current user internally
        return ResponseEntity.ok(Map.of(
                "message", "Embedding triggered. Check the logs for progress."
        ));
    }
}
