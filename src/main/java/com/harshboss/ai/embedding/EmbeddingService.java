package com.harshboss.ai.embedding;

import com.harshboss.entity.Email;
import com.harshboss.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embeds emails into the PGVector vector store for semantic search.
 *
 * <p>When an email is analyzed (or synced), this service embeds its
 * subject + body + sender into the vector store. Users can then search
 * "emails about the Q3 budget" and get semantically relevant results even
 * if those exact words don't appear.</p>
 *
 * <p>This is Layer 4 (Advanced RAG) of the architecture. Combined with the
 * agent's tools, the agent can retrieve emails by meaning, not just by
 * keyword match.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final EmailRepository emailRepository;

    /** Embed a single email into the vector store. */
    public void embedEmail(Email email) {
        if (email == null || email.getId() == null) return;
        try {
            String content = buildEmailText(email);
            Document doc = Document.builder()
                    .id(email.getId().toString())
                    .text(content)
                    .metadata(Map.of(
                            "emailId", email.getId().toString(),
                            "subject", email.getSubject(),
                            "from", email.getFromAddress(),
                            "folder", email.getFolder().name(),
                            "userId", email.getUserId() != null ? email.getUserId().toString() : ""
                    ))
                    .build();
            // Remove existing doc with same id first (in case of re-embedding)
            try {
                vectorStore.delete(List.of(email.getId().toString()));
            } catch (Exception ignored) {}
            vectorStore.add(List.of(doc));
            log.debug("Embedded email {} ({})", email.getId(), email.getSubject());
        } catch (Exception e) {
            log.warn("Failed to embed email {}: {}", email.getId(), e.getMessage());
        }
    }

    /** Embed all emails for a user (batch). */
    public int embedAllForUser(UUID userId) {
        List<Email> emails = emailRepository.findAllWithAnalysesForUser(userId, true);
        int count = 0;
        for (Email email : emails) {
            embedEmail(email);
            count++;
        }
        log.info("Embedded {} emails for user {}", count, userId);
        return count;
    }

    /**
     * Semantic search over the user's emails.
     * @param query natural-language query ("emails about the Q3 budget")
     * @param topK max results
     * @return list of (emailId, subject, from, score) matches
     */
    public List<SearchResult> search(String query, int topK) {
        try {
            var docs = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.5)
                            .build());
            return docs.stream()
                    .map(d -> new SearchResult(
                            d.getId(),
                            (String) d.getMetadata().get("emailId"),
                            (String) d.getMetadata().get("subject"),
                            (String) d.getMetadata().get("from"),
                            (String) d.getMetadata().get("folder"),
                            d.getScore() != null ? d.getScore() : 0.0
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Semantic search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildEmailText(Email email) {
        return "Subject: " + email.getSubject() + "\n"
                + "From: " + email.getFromName() + " <" + email.getFromAddress() + ">\n"
                + "Folder: " + email.getFolder() + "\n\n"
                + email.getBody();
    }

    /** Result of a semantic search. */
    public record SearchResult(
            String docId,
            String emailId,
            String subject,
            String from,
            String folder,
            double score
    ) {}
}
