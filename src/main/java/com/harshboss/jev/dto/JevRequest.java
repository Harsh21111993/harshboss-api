package com.harshboss.jev.dto;

import java.util.Map;

/**
 * Request DTOs for the Jev AI API (TypeSafe AI — System One decision model).
 *
 * <p>Jev takes an application <b>state</b> (raw text, JSON, or array) and one
 * or more typed <b>questions</b> (Choice, Score, or Noul), then returns typed
 * values + probabilities in a single forward pass (~100ms).</p>
 *
 * <p>Unlike an LLM, Jev does NOT generate text — it makes pure decisions.</p>
 */

/** A Choice question — selects one option from a categorical set. */
public record JevChoice(
    String instructions,
    Map<String, String> criteria
) {}

/** A Score question — rates the state against an ordered rubric. */
public record JevScore(
    String instructions,
    Map<String, String> rubric
) {}

/** A Noul question — evaluates a binary proposition (returns probability 0-1). */
public record JevNoul(
    String instructions,
    String proposition
) {}

/** The full request payload sent to Jev. */
public record JevRequest(
    Object state,
    Map<String, Object> questions
) {}
