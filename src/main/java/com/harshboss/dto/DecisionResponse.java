package com.harshboss.dto;

import com.harshboss.entity.enums.ApprovalStatus;

/**
 * Response of POST /api/approvals/{id}/decide.
 */
public record DecisionResponse(
        String approvalId,
        ApprovalStatus status,
        String notificationEmail
) {}
