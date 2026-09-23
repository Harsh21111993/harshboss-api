package com.harshboss.dto;

/**
 * Public profile of the workspace owner.
 * Returned by GET /api/users/me.
 */
public record UserDto(
        String id,
        String fullName,
        String email,
        String title,
        String avatarColor,
        String timezone,
        String profilePic
) {}
