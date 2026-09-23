package com.harshboss.dto;

import com.harshboss.entity.enums.EmailLabel;
import com.harshboss.entity.enums.Importance;

import java.util.List;

/**
 * AI triage result for an email. Returned both standalone (POST /analyze) and
 * nested inside {@link EmailDto}. Also used as the structured-output target
 * type for Spring AI's ChatClient.entity() call.
 */
public record EmailAnalysisDto(
        String brief,
        EmailLabel label,
        Importance importance,
        int score,
        String reason,
        String suggestedAction,
        List<String> actionItems,
        List<String> keyDates
) {}
