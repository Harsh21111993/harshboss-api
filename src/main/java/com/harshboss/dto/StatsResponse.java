package com.harshboss.dto;

/**
 * Response of GET /api/dashboard/stats — the four stat cards on the dashboard.
 */
public record StatsResponse(
        long unread,
        long important,
        long pendingApprovals,
        long meetingsToday
) {}
