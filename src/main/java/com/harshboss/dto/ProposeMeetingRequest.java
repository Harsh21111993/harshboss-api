package com.harshboss.dto;

import com.harshboss.entity.enums.Platform;
import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Body of POST /api/calendar/propose.
 */
public record ProposeMeetingRequest(
        @NotBlank String title,
        @NotNull Instant proposedStart,
        @NotNull @Min(1) @Max(480) Integer durationMinutes,
        @NotNull Platform platform,
        @NotBlank String requesterName,
        @NotBlank @jakarta.validation.constraints.Email String requesterEmail,
        String message
) {}
