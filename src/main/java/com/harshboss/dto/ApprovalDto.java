package com.harshboss.dto;

import com.harshboss.entity.enums.ApprovalStatus;
import com.harshboss.entity.enums.Platform;

import java.time.Instant;
import java.util.List;

/**
 * Pending / resolved meeting-proposal approval. Mirrors API_CONTRACT.md.
 */
public record ApprovalDto(
        String id,
        String type,
        String requesterName,
        String requesterEmail,
        Instant requestedTime,
        ApprovalStatus status,
        Instant createdAt,
        String message,
        ProposalDetails proposalDetails,
        ConflictInfo conflictInfo,
        List<String> alternatives,
        String autoReplySent,
        String decisionEmailSent
) {

    public record ProposalDetails(
            String title,
            Instant proposedStart,
            int durationMinutes,
            Platform platform
    ) {}

    public record ConflictInfo(
            String overlappingEventTitle,
            Instant overlappingEventStart,
            Instant overlappingEventEnd
    ) {}
}
