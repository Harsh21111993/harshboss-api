package com.harshboss.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Guardrail 2: Output Sanitizer
 *
 * <p>Scans AI-generated output for sensitive data that should never be
 * exposed to the user, and replaces it with redacted placeholders:</p>
 *
 * <ul>
 *   <li>API keys (sk-..., AIza..., AKIA..., ghp_...)</li>
 *   <li>JWT tokens (eyJ...)</li>
 *   <li>Passwords in plain text ("password: xxx")</li>
 *   <li>Credit card numbers</li>
 *   <li>SSNs (xxx-xx-xxxx)</li>
 *   <li>Private IP addresses (optional)</li>
 * </ul>
 *
 * <p>OWASP LLM Top 10 — LLM02: Sensitive Information Disclosure.</p>
 */
@Slf4j
@Service
public class OutputSanitizerGuardrail {

    @Value("${harshboss.guardrails.output-sanitizer.enabled:true}")
    private boolean enabled;

    // Regex patterns for sensitive data
    private static final Pattern API_KEY_OPENAI = Pattern.compile("sk-[a-zA-Z0-9]{20,}");
    private static final Pattern API_KEY_GOOGLE = Pattern.compile("AIza[a-zA-Z0-9_\\-]{35}");
    private static final Pattern API_KEY_AWS = Pattern.compile("AKIA[A-Z0-9]{16}");
    private static final Pattern API_KEY_GITHUB = Pattern.compile("gh[pousr]_[A-Za-z0-9]{36}");
    private static final Pattern JWT_TOKEN = Pattern.compile("eyJ[a-zA-Z0-9_\\-]+\\.eyJ[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+");
    private static final Pattern PASSWORD = Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+");
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]{20,}");

    /**
     * Sanitize the AI output — replace any sensitive data with [REDACTED].
     * @return the sanitized output (safe to show to the user)
     */
    public String sanitize(String output) {
        if (!enabled || output == null || output.isBlank()) {
            return output;
        }

        String sanitized = output;
        int replacements = 0;

        sanitized = API_KEY_OPENAI.matcher(sanitized).replaceAll("[REDACTED_API_KEY]");
        sanitized = API_KEY_GOOGLE.matcher(sanitized).replaceAll("[REDACTED_API_KEY]");
        sanitized = API_KEY_AWS.matcher(sanitized).replaceAll("[REDACTED_AWS_KEY]");
        sanitized = API_KEY_GITHUB.matcher(sanitized).replaceAll("[REDACTED_GITHUB_TOKEN]");
        sanitized = JWT_TOKEN.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = PASSWORD.matcher(sanitized).replaceAll("$1: [REDACTED]");
        sanitized = CREDIT_CARD.matcher(sanitized).replaceAll("[REDACTED_CC]");
        sanitized = SSN.matcher(sanitized).replaceAll("[REDACTED_SSN]");

        if (!sanitized.equals(output)) {
            log.warn("OUTPUT SANITIZER: redacted sensitive data from AI output");
        }

        return sanitized;
    }
}
