package com.harshboss.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response of POST /api/calendar/propose. status is "FREE" if the proposed
 * window had no conflicts, "CONFLICT" if it overlapped an existing event.
 */
public record ProposeMeetingResponse(
        String approvalId,
        Status status,
        ApprovalDto.ConflictInfo conflictInfo,
        List<String> alternatives,
        String autoReplyEmail
) {
    public enum Status { FREE, CONFLICT }
}
