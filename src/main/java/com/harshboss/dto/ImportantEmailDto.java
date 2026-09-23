package com.harshboss.dto;

import com.harshboss.entity.enums.EmailLabel;
import com.harshboss.entity.enums.Importance;

import java.time.Instant;
import java.util.List;

/**
 * Lightweight important-email summary returned to the UI.
 *
 * <p>Does NOT contain the full email body — only the AI's brief + a deep link
 * to view the full email in Gmail/Outlook.</p>
 */
public record ImportantEmailDto(
        String id,
        String provider,
        String providerMessageId,
        String providerUrl,
        String fromAddress,
        String fromName,
        String subject,
        String brief,
        EmailLabel label,
        Importance importance,
        int score,
        String reason,
        String suggestedAction,
        List<String> actionItems,
        Instant receivedAt,
        Instant detectedAt,
        boolean isMeetingInvitation
) {}
