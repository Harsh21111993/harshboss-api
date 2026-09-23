package com.harshboss.dto;

import com.harshboss.entity.enums.EmailFolder;
import com.harshboss.entity.enums.EmailLabel;
import com.harshboss.entity.enums.Importance;

import java.time.Instant;
import java.util.List;

/**
 * Email returned to the frontend. Field names match API_CONTRACT.md exactly.
 */
public record EmailDto(
        String id,
        String from,
        String fromName,
        String subject,
        String body,
        EmailFolder folder,
        boolean isRead,
        Instant receivedAt,
        EmailAnalysisDto analysis
) {}
