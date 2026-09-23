package com.harshboss.guardrails;

import java.lang.annotation.*;

/**
 * Unified guardrails annotation — applies all 5 guardrails to a method:
 *
 * <ol>
 *   <li><b>Content Moderation</b> — blocks harmful input (hate, violence, etc.)</li>
 *   <li><b>Topic Restriction</b> — keeps the conversation on work/productivity topics</li>
 *   <li><b>Input Length Limit</b> — rejects oversized inputs</li>
 *   <li><b>Output Sanitizer</b> — strips API keys, passwords, tokens from the response</li>
 *   <li><b>Hallucination Check</b> — verifies the response is grounded in real data</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}Guardrails
 * public String chat(String userMessage) { ... }
 * </pre>
 *
 * <p>Each guardrail can be individually toggled in application.yml:</p>
 * <pre>
 * atlas:
 *   guardrails:
 *     content-moderation:
 *       enabled: true
 *     output-sanitizer:
 *       enabled: true
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Guardrails {
    /** If true, checks input for harmful content before processing. */
    boolean checkInput() default true;

    /** If true, sanitizes the output for sensitive data after processing. */
    boolean sanitizeOutput() default true;

    /** If true, restricts the conversation to work/productivity topics. */
    boolean restrictTopic() default true;

    /** If true, checks the output for potential hallucinations. */
    boolean checkHallucination() default true;

    /** Max input length in characters. 0 = use config default. */
    int maxInputLength() default 0;
}
