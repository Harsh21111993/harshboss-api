package com.harshboss.dto;

/**
 * Response of POST /api/dashboard/daily-brief — the AI chief-of-staff summary.
 */
public record DailyBriefResponse(
        String brief
) {}
