package com.harshboss.guardrails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aspect that implements the {@link Guardrails} annotation.
 *
 * <p>Applies the full guardrail pipeline around any annotated method:</p>
 *
 * <pre>
 * ┌─ INPUT GUARDRAILS ──────────────────────────────────────┐
 * │  1. Input length limit (reject oversized inputs)       │
 * │  2. Content moderation (block harmful content)         │
 * │  3. Topic restriction (redirect off-topic requests)    │
 * └─────────────────────────────────────────────────────────┘
 *                          ↓
 *              ┌── method executes ──┐
 *                          ↓
 * ┌─ OUTPUT GUARDRAILS ─────────────────────────────────────┐
 * │  4. Hallucination check (verify grounded in data)      │
 * │  5. Output sanitizer (strip secrets/tokens)            │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class GuardrailsAspect {

    private final ContentModerationGuardrail contentModeration;
    private final OutputSanitizerGuardrail outputSanitizer;
    private final TopicRestrictionGuardrail topicRestriction;
    private final HallucinationCheckGuardrail hallucinationCheck;

    @Value("${harshboss.guardrails.input-length-limit.enabled:true}")
    private boolean inputLengthEnabled;

    @Value("${harshboss.guardrails.input-length-limit.max-chars:10000}")
    private int maxInputChars;

    @Around("@annotation(guardrails)")
    public Object applyGuardrails(ProceedingJoinPoint joinPoint, Guardrails guardrails) throws Throwable {
        // Extract the user input (first String argument)
        String userInput = extractUserInput(joinPoint.getArgs());

        // ═══ INPUT GUARDRAILS ═══

        // 1. Input length limit
        if (guardrails.checkInput() && inputLengthEnabled && userInput != null) {
            int limit = guardrails.maxInputLength() > 0 ? guardrails.maxInputLength() : maxInputChars;
            if (userInput.length() > limit) {
                log.warn("GUARDRAIL [INPUT LENGTH]: rejected input of {} chars (max: {})",
                        userInput.length(), limit);
                return "Your message is too long (" + userInput.length() + " chars, max: " + limit + "). " +
                       "Please shorten your request.";
            }
        }

        // 2. Content moderation
        if (guardrails.checkInput() && userInput != null) {
            String blocked = contentModeration.check(userInput);
            if (blocked != null) {
                return blocked;
            }
        }

        // 3. Topic restriction
        if (guardrails.restrictTopic() && userInput != null) {
            String redirect = topicRestriction.check(userInput);
            if (redirect != null) {
                return redirect;
            }
        }

        // ═══ EXECUTE METHOD ═══
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            // Sanitize error messages too (they might contain stack traces with secrets)
            String sanitizedError = outputSanitizer.sanitize(e.getMessage());
            throw new RuntimeException(sanitizedError, e);
        }

        // ═══ OUTPUT GUARDRAILS ═══

        if (result instanceof String responseText) {
            // 4. Hallucination check
            if (guardrails.checkHallucination()) {
                String warning = hallucinationCheck.check(responseText, null);
                if (warning != null) {
                    responseText = responseText + warning;
                }
            }

            // 5. Output sanitizer
            if (guardrails.sanitizeOutput()) {
                responseText = outputSanitizer.sanitize(responseText);
            }

            return responseText;
        }

        return result;
    }

    /** Extract the first String argument as the user input. */
    private String extractUserInput(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object arg : args) {
            if (arg instanceof String s) {
                return s;
            }
        }
        return null;
    }
}
