package com.harshboss.integration;

import com.harshboss.entity.Email;
import com.harshboss.entity.enums.EmailFolder;
import com.harshboss.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

/**
 * REAL Gmail client — fetches actual emails from the user's Gmail mailbox
 * using the stored OAuth2 access token.
 *
 * <p>Requires the user to connect their Google account first
 * (GET /api/oauth/connect/google).</p>
 *
 * <p>Gmail's API requires 2 calls per email (list + get), so this is capped
 * at a small maxResults (default 20) to avoid rate limits.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealGmailClient {

    private final WebClient.Builder webClientBuilder;
    private final OAuth2Service oauth2Service;

    private static final String GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1";

    /**
     * Fetch up to {@code maxResults} emails from the user's Gmail inbox.
     */
    public List<Email> fetchRealEmails(UUID userId, int maxResults) {
        String token = oauth2Service.getValidToken(
                com.harshboss.entity.enums.OAuthProvider.GOOGLE).getAccessToken();

        // 1. List message IDs
        String listUrl = GMAIL_BASE + "/users/me/messages?maxResults=" + maxResults;
        Map<String, Object> listResp;
        try {
            listResp = webClientBuilder.build().get()
                    .uri(listUrl)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Gmail list failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to list Gmail messages: " + e.getMessage());
        }

        List<Map<String, Object>> messages = listResp != null
                ? (List<Map<String, Object>>) listResp.get("messages") : null;
        if (messages == null || messages.isEmpty()) {
            log.info("Gmail: no messages found for user {}", userId);
            return Collections.emptyList();
        }

        // 2. Fetch each message's details
        List<Email> result = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            String msgId = (String) m.get("id");
            try {
                Map<String, Object> msg = webClientBuilder.build().get()
                        .uri(GMAIL_BASE + "/users/me/messages/" + msgId
                                + "?format=full&metadataHeaders=Subject&metadataHeaders=From")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                if (msg != null) {
                    Email email = mapMessage(msg, userId);
                    if (email != null) result.add(email);
                }
            } catch (Exception e) {
                log.warn("Gmail: skipping message {} ({})", msgId, e.getMessage());
            }
        }
        log.info("Gmail: fetched {} emails for user {}", result.size(), userId);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Email mapMessage(Map<String, Object> msg, UUID userId) {
        Email email = new Email();
        email.setUserId(userId);
        email.setFolder(EmailFolder.INBOX);

        // payload.headers is a list of {name, value}
        Map<String, Object> payload = (Map<String, Object>) msg.get("payload");
        String fromHeader = null;
        String subject = null;
        if (payload != null) {
            List<Map<String, Object>> headers = (List<Map<String, Object>>) payload.get("headers");
            if (headers != null) {
                for (Map<String, Object> h : headers) {
                    String name = (String) h.get("name");
                    if ("From".equalsIgnoreCase(name)) fromHeader = (String) h.get("value");
                    else if ("Subject".equalsIgnoreCase(name)) subject = (String) h.get("value");
                }
            }
        }

        // Parse "Display Name <email@domain>" format
        if (fromHeader != null) {
            int lt = fromHeader.indexOf('<');
            int gt = fromHeader.indexOf('>');
            if (lt >= 0 && gt > lt) {
                email.setFromName(fromHeader.substring(0, lt).trim().replace("\"", ""));
                email.setFromAddress(fromHeader.substring(lt + 1, gt).trim());
            } else {
                email.setFromAddress(fromHeader.trim());
                email.setFromName(fromHeader.trim());
            }
        } else {
            email.setFromAddress("unknown@gmail.com");
            email.setFromName("Unknown");
        }

        email.setSubject(subject != null ? subject : "(no subject)");

        // snippet as body (full body parsing would require decoding base64 parts)
        email.setBody((String) msg.getOrDefault("snippet", ""));

        // isRead: labelIds contains "UNREAD" → unread
        List<String> labelIds = (List<String>) msg.get("labelIds");
        email.setRead(labelIds == null || !labelIds.contains("UNREAD"));

        // internalDate is epoch millis as a string
        String internalDate = (String) msg.get("internalDate");
        if (internalDate != null) {
            try {
                email.setReceivedAt(Instant.ofEpochMilli(Long.parseLong(internalDate)));
            } catch (NumberFormatException e) {
                email.setReceivedAt(Instant.now());
            }
        } else {
            email.setReceivedAt(Instant.now());
        }
        return email;
    }
}
