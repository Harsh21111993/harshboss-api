package com.harshboss.controller;

import com.harshboss.dto.OAuth2Dtos;
import com.harshboss.entity.UserOauthToken;
import com.harshboss.entity.enums.OAuthProvider;
import com.harshboss.repository.UserOauthTokenRepository;
import com.harshboss.service.AdvancedFeaturesService;
import com.harshboss.service.SmartSyncService;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Email sync controller — manual + automatic sync endpoints.
 *
 * <p>POST /api/sync/emails?provider=google&maxResults=25
 *  → sync from a specific provider</p>
 *
 * <p>POST /api/sync/all?maxResults=25
 *  → sync from ALL connected providers at once (used by the scheduler + manual "Sync All" button)</p>
 *
 * <p>GET /api/sync/status
 *  → returns the sync status (which providers are connected, last sync info)</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class EmailSyncController {

    private final SmartSyncService smartSyncService;
    private final UserService userService;
    private final UserOauthTokenRepository tokenRepository;
    private final AdvancedFeaturesService advancedFeaturesService;

    /** POST /api/sync/emails?provider=google&maxResults=25 — sync from one provider */
    @PostMapping("/emails")
    public ResponseEntity<OAuth2Dtos.EmailSyncResult> syncEmails(
            @RequestParam String provider,
            @RequestParam(defaultValue = "25") int maxResults) {
        log.info("POST /api/sync/emails?provider={}&maxResults={}", provider, maxResults);
        return ResponseEntity.ok(smartSyncService.syncEmails(provider, maxResults));
    }

    /**
     * POST /api/sync/all?maxResults=25 — sync from ALL connected providers.
     * This is what the scheduler calls, and what the "Sync All" button calls.
     */
    @PostMapping("/all")
    public ResponseEntity<List<OAuth2Dtos.EmailSyncResult>> syncAll(
            @RequestParam(defaultValue = "25") int maxResults) {
        UUID userId = userService.requireCurrentUserId();
        List<UserOauthToken> tokens = tokenRepository.findByUserId(userId);
        List<OAuth2Dtos.EmailSyncResult> results = new ArrayList<>();

        if (tokens.isEmpty()) {
            log.info("POST /api/sync/all — no providers connected for user {}", userId);
            return ResponseEntity.ok(results);
        }

        for (UserOauthToken token : tokens) {
            OAuthProvider provider = token.getProvider();
            try {
                OAuth2Dtos.EmailSyncResult result = smartSyncService.syncEmails(
                        provider.name().toLowerCase(), maxResults);
                results.add(result);

                // Create notification for new important emails
                if (result.newEmails() > 0) {
                    advancedFeaturesService.createNotification(
                            userId,
                            "HIGH_EMAIL",
                            result.newEmails() + " new important email" + (result.newEmails() == 1 ? "" : "s"),
                            "From " + provider.name() + ": " + result.message(),
                            null
                    );
                }
            } catch (Exception e) {
                log.error("Sync failed for {}: {}", provider, e.getMessage());
                results.add(new OAuth2Dtos.EmailSyncResult(
                        provider.name(), 0, 0, 0, "Sync failed: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(results);
    }

    /** GET /api/sync/status — returns which providers are connected */
    @GetMapping("/status")
    public ResponseEntity<SyncStatus> syncStatus() {
        UUID userId = userService.requireCurrentUserId();
        List<UserOauthToken> tokens = tokenRepository.findByUserId(userId);

        boolean googleConnected = tokens.stream()
                .anyMatch(t -> t.getProvider() == OAuthProvider.GOOGLE);
        boolean microsoftConnected = tokens.stream()
                .anyMatch(t -> t.getProvider() == OAuthProvider.MICROSOFT);

        return ResponseEntity.ok(new SyncStatus(googleConnected, microsoftConnected, tokens.size()));
    }

    public record SyncStatus(boolean googleConnected, boolean microsoftConnected, int totalProviders) {}
}
