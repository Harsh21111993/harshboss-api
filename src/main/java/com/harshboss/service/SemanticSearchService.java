package com.harshboss.service;

import com.harshboss.ai.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Semantic search over the user's emails via PGVector embeddings.
 *
 * <p>Users can ask "emails about the Q3 budget" and get semantically relevant
 * results — even if those exact words don't appear in the email.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final EmbeddingService embeddingService;

    public List<EmbeddingService.SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        int limit = Math.max(1, Math.min(20, topK));
        log.info("Semantic search: '{}' (top {})", query, limit);
        return embeddingService.search(query, limit);
    }
}
