package com.harshboss.aspect.annotation;

import java.lang.annotation.*;

/**
 * Rate-limits a method — prevents more than N calls per time window.
 * Throws a 429 Too Many Requests if the limit is exceeded.
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}RateLimit(maxCalls = 10, windowSeconds = 60)
 * public EmailAnalysisDto analyzeEmail(UUID emailId) { ... }
 * </pre>
 *
 * <p>Effect: max 10 calls per 60 seconds. The 11th call throws:</p>
 * <pre>
 * Rate limit exceeded: max 10 calls per 60 seconds for analyzeEmail
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** Maximum calls allowed in the time window. */
    int maxCalls() default 10;

    /** Time window in seconds. */
    int windowSeconds() default 60;
}
