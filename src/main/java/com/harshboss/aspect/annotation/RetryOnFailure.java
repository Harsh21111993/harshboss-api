package com.harshboss.aspect.annotation;

import java.lang.annotation.*;

/**
 * Automatically retries a method on failure (e.g., transient LLM API errors).
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}RetryOnFailure(maxAttempts = 3, delayMs = 1000)
 * public String draftReply(ImportantEmail email, String tone) { ... }
 * </pre>
 *
 * <p>If the method throws an exception, it retries up to {@code maxAttempts}
 * times with a {@code delayMs} delay between attempts. If all attempts fail,
 * the last exception is re-thrown.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryOnFailure {
    /** Max retry attempts (including the first call). Default 3. */
    int maxAttempts() default 3;

    /** Delay between retries in milliseconds. Default 1000ms. */
    long delayMs() default 1000L;

    /** Exception types that should trigger a retry. Empty = retry on all. */
    Class<? extends Throwable>[] retryOn() default {};
}
