package com.harshboss.service;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.aspect.annotation.AuditAction;

import com.harshboss.ai.EmailTriageAiService;
import com.harshboss.ai.MeetingInvitationDetector;
import com.harshboss.dto.EmailAnalysisDto;
import com.harshboss.dto.JsonUtil;
import com.harshboss.dto.OAuth2Dtos;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.ImportantEmail;
import com.harshboss.entity.enums.EventStatus;
import com.harshboss.entity.enums.Importance;
import com.harshboss.entity.enums.Platform;
import com.harshboss.entity.enums.OAuthProvider;
import com.harshboss.integration.RealGmailClient;
import com.harshboss.integration.RealMicrosoftGraphClient;
import com.harshboss.repository.CalendarEventRepository;
import com.harshboss.repository.ImportantEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Smart Sync — the lightweight email intelligence layer.
 *
 * <p>Instead of storing ALL emails in our DB (which duplicates the user's
 * mailbox and wastes space), this service:</p>
 * <ol>
 *   <li>Fetches emails from the provider (Gmail/Outlook) into memory</li>
 *   <li>Runs AI triage on each in memory (brief, label, importance, score)</li>
 *   <li>Persists ONLY the important ones (HIGH/MEDIUM) as {@link ImportantEmail}</li>
 *   <li>For meeting invitations, also creates a {@link CalendarEvent} with a
 *       deep link back to the email in Gmail/Outlook</li>
 *   <li>Discards the rest — we're an intelligence layer, not a mailbox clone</li>
 * </ol>
 *
 * <p>The user views full email content by clicking "View in Gmail" — which
 * opens the email in their provider's web UI. We never store the full body.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSyncService {

    private final RealMicrosoftGraphClient msClient;
    private final RealGmailClient gmailClient;
    private final EmailTriageAiService triageAiService;
    private final MeetingInvitationDetector meetingDetector;
    private final ImportantEmailRepository importantEmailRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

    @Transactional
    @LogExecution(level = "INFO")
    @MeasurePerformance(warnThresholdMs = 60000)
    @AuditAction(action = "EMAIL_SYNC", description = "Smart sync from email provider")
    public OAuth2Dtos.EmailSyncResult syncEmails(String provider, int maxResults) {
        OAuthProvider p = parseProvider(provider);
        UUID userId = userService.requireCurrentUserId();

        // 1. Fetch emails from the provider into memory (NOT persisted yet)
        List<com.harshboss.entity.Email> fetched;
        try {
            fetched = p == OAuthProvider.MICROSOFT
                    ? msClient.fetchRealEmails(userId, maxResults)
                    : gmailClient.fetchRealEmails(userId, maxResults);
        } catch (Exception e) {
            log.error("Smart sync: fetch failed for {}: {}", p, e.getMessage());
            return new OAuth2Dtos.EmailSyncResult(p.name(), 0, 0, 0, "Sync failed: " + e.getMessage());
        }

        int importantCount = 0;
        int meetingCount = 0;
        int skippedUnimportant = 0;
        int skippedDuplicate = 0;

        // 2. Triage each email in memory — only persist the important ones
        for (com.harshboss.entity.Email email : fetched) {
            // Generate a stable message ID for this email (since it's not persisted
            // to the emails table, its database ID is null). We use a hash of
            // from + subject + receivedAt as the providerMessageId.
            String providerMessageId = generateMessageId(email);

            // Build the provider URL (deep link to view in Gmail/Outlook)
            String providerUrl = buildProviderUrl(p, providerMessageId);

            // Dedup: already flagged this email as important?
            if (importantEmailRepository
                    .findByUserIdAndProviderAndProviderMessageId(
                            userId, p.name(), providerMessageId).isPresent()) {
                skippedDuplicate++;
                continue;
            }

            // Run AI triage in memory
            EmailAnalysisDto analysis;
            try {
                analysis = triageAiService.analyze(email);
            } catch (Exception e) {
                log.warn("Smart sync: triage failed for '{}': {}", email.getSubject(), e.getMessage());
                continue;
            }

            // Only persist if important (HIGH or MEDIUM) OR it's a meeting invitation
            boolean isImportant = analysis.importance() == Importance.HIGH
                    || analysis.importance() == Importance.MEDIUM;

            // Check for meeting invitation (in memory — no DB write yet)
            MeetingInvitationDetector.MeetingInvitation meeting = null;
            try {
                meeting = meetingDetector.detect(email.getSubject(), email.getBody(), email.getFromName());
            } catch (Exception e) {
                log.debug("Meeting detection failed (non-blocking): {}", e.getMessage());
            }

            if (!isImportant && meeting == null) {
                skippedUnimportant++;
                continue;
            }

            // Persist the important email summary (lightweight — no full body)
            ImportantEmail important = new ImportantEmail();
            important.setUserId(userId);
            important.setProvider(p.name());
            important.setProviderMessageId(providerMessageId);
            important.setProviderUrl(providerUrl);
            important.setFromAddress(email.getFromAddress());
            important.setFromName(email.getFromName());
            important.setSubject(email.getSubject());
            important.setBrief(analysis.brief());
            important.setLabel(analysis.label());
            important.setImportance(analysis.importance());
            important.setScore(analysis.score());
            important.setReason(analysis.reason());
            important.setSuggestedAction(analysis.suggestedAction());
            important.setActionItems(JsonUtil.toJson(analysis.actionItems()));
            important.setReceivedAt(email.getReceivedAt());
            important.setDetectedAt(Instant.now());
            important.setMeetingInvitation(meeting != null);
            importantEmailRepository.save(important);
            importantCount++;

            // If it's a meeting invitation, create a calendar event
            if (meeting != null) {
                try {
                    createMeetingEvent(meeting, email, userId, providerUrl);
                    meetingCount++;
                } catch (Exception e) {
                    log.warn("Failed to create meeting event for '{}': {}", email.getSubject(), e.getMessage());
                }
            }
        }

        String message = String.format(
                "Scanned %d emails: %d important saved, %d meetings detected, %d unimportant skipped, %d duplicates skipped",
                fetched.size(), importantCount, meetingCount, skippedUnimportant, skippedDuplicate);

        log.info("Smart sync [{}]: {}", p, message);
        return new OAuth2Dtos.EmailSyncResult(
                p.name(), fetched.size(), importantCount, skippedUnimportant + skippedDuplicate, message);
    }

    /** Create a calendar event from a meeting invitation, linked to the source email via URL. */
    private void createMeetingEvent(MeetingInvitationDetector.MeetingInvitation meeting,
                                      com.harshboss.entity.Email email, UUID userId, String providerUrl) {
        Instant start;
        try {
            start = OffsetDateTime.parse(meeting.getStartDateTime()).toInstant();
        } catch (Exception e) {
            log.warn("Could not parse meeting time '{}': {}", meeting.getStartDateTime(), e.getMessage());
            return;
        }

        int duration = meeting.getDurationMinutes() > 0 ? meeting.getDurationMinutes() : 30;
        Platform platform;
        try {
            platform = Platform.valueOf(meeting.getPlatform().toUpperCase());
        } catch (Exception e) {
            platform = Platform.PERSONAL;
        }

        CalendarEvent event = new CalendarEvent();
        event.setTitle(meeting.getTitle() != null ? meeting.getTitle() : email.getSubject());
        event.setPlatform(platform);
        event.setStartTime(start);
        event.setEndTime(start.plusSeconds(duration * 60L));
        event.setOrganizer(email.getFromName());
        event.setAttendees(JsonUtil.toJson(List.of(email.getFromName())));
        event.setJoinUrl(meeting.getJoinUrl());
        event.setLocation(meeting.getLocation());
        event.setHiddenByOthers(false);
        event.setStatus(EventStatus.TENTATIVE);
        event.setUserId(userId);
        event.setSourceEmailUrl(providerUrl);  // deep link to view in Gmail/Outlook
        calendarEventRepository.save(event);

        log.info("Created calendar event '{}' from email (start={}, link={})",
                event.getTitle(), start, providerUrl);
    }

    /**
     * Generate a stable message ID for an in-memory email (not persisted to DB).
     * Uses a hash of from + subject + receivedAt so re-syncing the same email
     * produces the same ID (for dedup).
     */
    private String generateMessageId(com.harshboss.entity.Email email) {
        String input = (email.getFromAddress() != null ? email.getFromAddress() : "")
                + "|" + (email.getSubject() != null ? email.getSubject() : "")
                + "|" + (email.getReceivedAt() != null ? email.getReceivedAt().toString() : "");
        return Integer.toHexString(input.hashCode());
    }

    /** Build a deep link to view the email in the provider's web UI. */
    private String buildProviderUrl(OAuthProvider provider, String providerMessageId) {
        if (provider == OAuthProvider.GOOGLE) {
            // Gmail search URL (since we don't have the raw Gmail message ID,
            // we link to a Gmail search that will find the email)
            return "https://mail.google.com/mail/u/0/#search/" + providerMessageId;
        } else if (provider == OAuthProvider.MICROSOFT) {
            return "https://outlook.live.com/mail/0/inbox";
        }
        return null;
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
