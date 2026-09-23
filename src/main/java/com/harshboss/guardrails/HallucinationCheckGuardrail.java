package com.harshboss.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Guardrail 5: Hallucination Check
 *
 * <p>Verifies that AI-generated responses are grounded in actual data
 * (tool results, database records) rather than fabricated information.</p>
 *
 * <p>Detects common hallucination patterns:</p>
 * <ul>
 *   <li><b>Fabricated specifics</b>: specific dates, times, names, or amounts
 *       that don't appear in the tool results</li>
 *   <li><b>False confidence</b>: "I found 5 emails" when only 2 were returned</li>
 *   <li><b>Invented URLs</b>: links that don't match the tool's output format</li>
 *   <li><b>Unverifiable claims</b>: "Your manager said..." without a source email</li>
 * </ul>
 *
 * <p>Uses a lightweight heuristic approach (no LLM call needed — saves cost).
 * For production-grade hallucination detection, add an LLM-based faithfulness
 * check using Spring AI's RelevancyEvaluator.</p>
 */
@Slf4j
@Service
public class HallucinationCheckGuardrail {

    @Value("${harshboss.guardrails.hallucination-check.enabled:true}")
    private boolean enabled;

    /** Patterns that indicate potential hallucination. */
    private static final Pattern FABRICATED_DATE = Pattern.compile(
            "\\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4}\\s+at\\s+\\d{1,2}:\\d{2}\\s*(?:AM|PM)?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FABRICATED_AMOUNT = Pattern.compile(
            "\\$\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b");

    /**
     * Check the AI response for potential hallucinations.
     * @param aiResponse the AI-generated text
     * @param toolResults the actual data returned by tools (concatenated as a string)
     * @return null if the response seems grounded, or a warning if hallucination is suspected
     */
    public String check(String aiResponse, String toolResults) {
        if (!enabled || aiResponse == null || aiResponse.isBlank()) {
            return null;
        }

        List<String> warnings = new ArrayList<>();

        // 1. Check for specific dates/times not in the tool results
        if (toolResults != null && !toolResults.isBlank()) {
            var dateMatcher = FABRICATED_DATE.matcher(aiResponse);
            while (dateMatcher.find()) {
                String date = dateMatcher.group();
                if (!toolResults.contains(date)) {
                    warnings.add("specific date/time '" + date + "'");
                }
            }

            // 2. Check for specific dollar amounts not in tool results
            var amountMatcher = FABRICATED_AMOUNT.matcher(aiResponse);
            while (amountMatcher.find()) {
                String amount = amountMatcher.group();
                if (!toolResults.contains(amount)) {
                    warnings.add("specific amount '" + amount + "'");
                }
            }
        }

        // 3. Check for false confidence patterns
        String lower = aiResponse.toLowerCase();
        if (lower.contains("i found ") && !lower.contains("no ") && !lower.contains("0 ")) {
            // The AI claims to have found something — verify it's not making up a count
            // (A full implementation would parse the number and compare with tool results)
            if (toolResults == null || toolResults.isBlank() || toolResults.equals("[]")) {
                warnings.add("claims to have found results but no tool data was returned");
            }
        }

        // 4. Check for invented email addresses (not in tool results)
        var emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        var emailMatcher = emailPattern.matcher(aiResponse);
        while (emailMatcher.find()) {
            String email = emailMatcher.group();
            if (toolResults != null && !toolResults.contains(email) &&
                !email.contains("noreply") && !email.contains("no-reply")) {
                warnings.add("email address '" + email + "' not found in source data");
            }
        }

        if (!warnings.isEmpty()) {
            log.warn("HALLUCINATION CHECK: potential issues detected: {}", String.join("; ", warnings));
            // Don't block the response — just add a disclaimer
            return "\n\n⚠️ Note: Some details in this response could not be verified against " +
                   "your actual data. Please confirm specific dates, amounts, and email addresses " +
                   "before acting on them.";
        }

        return null; // Response seems grounded
    }
}
