package com.harshboss.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/approvals/{id}/decide.
 */
public record DecisionRequest(
        @NotNull Decision decision
) {
    public enum Decision { APPROVE, DECLINE }
}
