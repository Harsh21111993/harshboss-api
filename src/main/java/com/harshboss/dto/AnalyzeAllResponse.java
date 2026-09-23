package com.harshboss.dto;

import java.util.List;

/**
 * Response of POST /api/emails/analyze-all — per-email result, with error
 * captured instead of throwing so a single bad email doesn't abort the batch.
 */
public record AnalyzeAllResponse(
        List<AnalyzeResult> results
) {
    public record AnalyzeResult(
            String emailId,
            EmailAnalysisDto analysis,
            String error
    ) {}
}
