package com.harshboss.service;

import com.harshboss.dto.JsonUtil;
import com.harshboss.dto.OAuth2Dtos;
import com.harshboss.entity.Email;
import com.harshboss.entity.enums.OAuthProvider;
import com.harshboss.integration.RealGmailClient;
import com.harshboss.integration.RealMicrosoftGraphClient;
import com.harshboss.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches REAL emails from the connected provider's API, persists them to the
 * emails table with the current user's id, and deduplicates by
 * (userId + fromAddress + subject + receivedAt) so re-syncing doesn't create
 * duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSyncService {

    private final EmailRepository emailRepository;
    private final RealMicrosoftGraphClient msClient;
    private final RealGmailClient gmailClient;
    private final UserService userService;

    /**
     * Sync emails from the given provider for the current user.
     *
     * @param provider   "microsoft" or "google"
     * @param maxResults max emails to fetch (default 20)
     */
    @Transactional
    public OAuth2Dtos.EmailSyncResult syncEmails(String provider, int maxResults) {
        OAuthProvider p = parseProvider(provider);
        UUID userId = userService.requireCurrentUserId();
        List<Email> fetched;

        try {
            if (p == OAuthProvider.MICROSOFT) {
                fetched = msClient.fetchRealEmails(userId, maxResults);
            } else {
                fetched = gmailClient.fetchRealEmails(userId, maxResults);
            }
        } catch (Exception e) {
            log.error("Email sync failed for {}: {}", p, e.getMessage());
            return new OAuth2Dtos.EmailSyncResult(
                    p.name(), 0, 0, 0, "Sync failed: " + e.getMessage());
        }

        int newCount = 0;
        int skipped = 0;

        for (Email email : fetched) {
            // Dedup: skip if an email with same (userId, fromAddress, subject, receivedAt) exists
            Optional<Email> existing = emailRepository.findAllWithAnalyses(false).stream()
                    .filter(e -> e.getUserId() != null
                            && e.getUserId().equals(email.getUserId())
                            && e.getFromAddress().equals(email.getFromAddress())
                            && e.getSubject().equals(email.getSubject())
                            && e.getReceivedAt().equals(email.getReceivedAt()))
                    .findFirst();
            if (existing.isPresent()) {
                skipped++;
                continue;
            }
            emailRepository.save(email);
            newCount++;
        }

        String message = newCount > 0
                ? "Synced " + newCount + " new email" + (newCount == 1 ? "" : "s")
                  + " from " + p.name() + (skipped > 0 ? " (" + skipped + " already synced)" : "")
                : "No new emails — " + skipped + " were already synced";

        log.info("Email sync [{}]: fetched={}, new={}, skipped={}", p, fetched.size(), newCount, skipped);
        return new OAuth2Dtos.EmailSyncResult(p.name(), fetched.size(), newCount, skipped, message);
    }

    private OAuthProvider parseProvider(String s) {
        if (s == null) throw new IllegalArgumentException("Provider is required");
        return switch (s.toLowerCase()) {
            case "microsoft", "ms", "outlook" -> OAuthProvider.MICROSOFT;
            case "google", "gmail" -> OAuthProvider.GOOGLE;
            default -> throw new IllegalArgumentException("Unknown provider: " + s);
        };
    }
}
