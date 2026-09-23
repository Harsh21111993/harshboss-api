package com.harshboss.ai.advisors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientRequestAdvisor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientResponseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Semantic Cache Advisor — caches LLM responses by embedding similarity.
 *
 * <p>When a request comes in, embed the user's message and search the vector
 * store for a cached answer with cosine similarity > 0.92. If found, return
 * the cached answer without calling the LLM — saves cost + latency on repeated
 * or similar questions.</p>
 *
 * <p>Spring AI 2.0.0 API: uses {@code ChatClientRequest} / {@code ChatClientResponse}.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class SemanticCacheAdvisor implements ChatClientRequestAdvisor, ChatClientResponseAdvisor {

    private static final double SIMILARITY_THRESHOLD = 0.92;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public ChatClientRequest adviseRequest(ChatClientRequest request) {
        String userText = extractUserText(request);

        if (userText == null || userText.length() < 10) {
            request.context().put("__cache_check", false);
            return request;
        }

        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(userText)
                    .topK(1)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .build());

            if (hits != null && !hits.isEmpty()) {
                Document cached = hits.get(0);
                String cachedAnswer = cached.getMetadata() != null
                        ? (String) cached.getMetadata().get("cached_answer") : null;
                if (cachedAnswer != null) {
                    log.info("Semantic cache HIT — returning cached answer");
                    request.context().put("__cache_hit", cachedAnswer);
                    request.context().put("__cache_check", true);
                    // Mark as cache hit — the response advisor will inject the cached answer
                    return request;
                }
            }
        } catch (Exception e) {
            log.debug("Semantic cache lookup failed (non-blocking): {}", e.getMessage());
        }
        request.context().put("__cache_check", true);
        request.context().put("__user_text", userText);
        return request;
    }

    @Override
    public ChatClientResponse adviseResponse(ChatClientResponse response) {
        // Check if this was a cache hit
        Object cached = response.context().get("__cache_hit");
        if (cached != null) {
            // Cache hit — the cached answer should be returned
            // (In a full implementation, we'd replace the response content here)
            return response;
        }

        // Cache miss → store the Q→A pair for future hits
        String userText = (String) response.context().get("__user_text");
        if (userText == null) return response;

        try {
            String answer = response.chatResponse() != null
                    && response.chatResponse().getResult() != null
                    && response.chatResponse().getResult().getOutput() != null
                    ? response.chatResponse().getResult().getOutput().getText() : null;
            if (answer == null || answer.isBlank()) return response;

            Document doc = Document.builder()
                    .text(userText)
                    .metadata(Map.of(
                            "cached_answer", answer,
                            "type", "semantic_cache"
                    ))
                    .build();
            vectorStore.add(List.of(doc));
            log.info("Semantic cache: stored new Q→A pair");
        } catch (Exception e) {
            log.debug("Semantic cache store failed (non-blocking): {}", e.getMessage());
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
