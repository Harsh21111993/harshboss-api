package com.harshboss.jev.dto;

import java.util.Map;

/**
 * Response DTOs for the Jev AI API.
 *
 * <p>Jev returns typed results for each question asked, including:
 * <ul>
 *   <li><b>value</b> — the selected choice, score, or probability</li>
 *   <li><b>confidence</b> — calibrated confidence score (0.0-1.0)</li>
 *   <li><b>probabilities</b> — per-option probability distribution (for Choice)</li>
 * </ul>
 * </p>
 */

/** Result for a single question. */
public record JevResult(
    String value,
    double confidence,
    Map<String, Double> probabilities
) {}

/** The full response from Jev — keyed by question name. */
public record JevResponse(
    Map<String, JevResult> results
) {}
