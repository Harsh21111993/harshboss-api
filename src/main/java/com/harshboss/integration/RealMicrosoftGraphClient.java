package com.harshboss.integration;

import com.harshboss.entity.Email;
import com.harshboss.entity.enums.EmailFolder;
import com.harshboss.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * REAL Microsoft Graph client — fetches actual emails from the user's Outlook
 * mailbox using the stored OAuth2 access token.
 *
 * <p>Requires the user to connect their Microsoft account first
 * (GET /api/oauth/connect/microsoft).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealMicrosoftGraphClient {

    private final WebClient.Builder webClientBuilder;
    private final OAuth2Service oauth2Service;

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    /**
     * Fetch up to {@code maxResults} emails from the user's Outlook inbox.
     * Returns Email entities (NOT yet persisted — the caller saves).
     */
    @SuppressWarnings("unchecked")
    public List<Email> fetchRealEmails(UUID userId, int maxResults) {
        String token = oauth2Service.getValidToken(
                com.harshboss.entity.enums.OAuthProvider.MICROSOFT).getAccessToken();

        String url = GRAPH_BASE + "/me/messages"
                + "?$top=" + maxResults
                + "&$select=subject,from,bodyPreview,receivedDateTime,body,isRead"
                + "&$orderby=receivedDateTime desc";

        Map<String, Object> resp;
        try {
            resp = webClientBuilder.build().get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("MS Graph fetch failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to fetch emails from Microsoft Graph: " + e.getMessage());
        }

        List<Email> result = new ArrayList<>();
        List<Map<String, Object>> messages = resp != null ? (List<Map<String, Object>>) resp.get("value") : null;
        if (messages == null) return result;

        for (Map<String, Object> msg : messages) {
            try {
                Email email = mapMessage(msg, userId);
                if (email != null) result.add(email);
            } catch (Exception e) {
                log.warn("Skipping malformed MS Graph message: {}", e.getMessage());
            }
        }
        log.info("MS Graph: fetched {} emails for user {}", result.size(), userId);
        return result;
    }

    private Email mapMessage(Map<String, Object> msg, UUID userId) {
        Email email = new Email();
        email.setUserId(userId);
        email.setFolder(EmailFolder.INBOX);

        // from → { emailAddress: { name, address } }
        Map<String, Object> from = (Map<String, Object>) msg.get("from");
        if (from != null) {
            Map<String, Object> addr = (Map<String, Object>) from.get("emailAddress");
            if (addr != null) {
                email.setFromAddress((String) addr.getOrDefault("address", "unknown@unknown.com"));
                email.setFromName((String) addr.getOrDefault("name", email.getFromAddress()));
            }
        } else {
            email.setFromAddress("unknown@unknown.com");
            email.setFromName("Unknown");
        }

        email.setSubject((String) msg.getOrDefault("subject", "(no subject)"));

        // body → { contentType: "text"|"html", content: "..." }
        Map<String, Object> body = (Map<String, Object>) msg.get("body");
        if (body != null) {
            String content = (String) body.get("content");
            String contentType = (String) body.getOrDefault("contentType", "text");
            email.setBody(content != null ? stripHtml(content) : msg.getOrDefault("bodyPreview", "").toString());
        } else {
            email.setBody((String) msg.getOrDefault("bodyPreview", ""));
        }

        email.setRead(Boolean.TRUE.equals(msg.get("isRead")));

        String received = (String) msg.get("receivedDateTime");
        if (received != null) {
            email.setReceivedAt(OffsetDateTime.parse(received).toInstant());
        } else {
            email.setReceivedAt(Instant.now());
        }
        return email;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }
}
