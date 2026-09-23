package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.RetryOnFailure;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect that implements the {@link RetryOnFailure} annotation.
 *
 * <p>Retries the method on failure with an exponential backoff delay.
 * Perfect for transient errors (LLM API timeouts, network blips).</p>
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}RetryOnFailure(maxAttempts = 3, delayMs = 1000)
 * public String draftReply(ImportantEmail email, String tone) { ... }
 * </pre>
 */
@Slf4j
@Aspect
@Component
public class RetryOnFailureAspect {

    @Around("@annotation(retryOnFailure)")
    public Object retry(ProceedingJoinPoint joinPoint, RetryOnFailure retryOnFailure) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        int maxAttempts = retryOnFailure.maxAttempts();
        long delayMs = retryOnFailure.delayMs();
        Class<? extends Throwable>[] retryOn = retryOnFailure.retryOn();

        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object result = joinPoint.proceed();

                if (attempt > 1) {
                    log.info("RETRY: {} succeeded on attempt {}/{}", method, attempt, maxAttempts);
                }
                return result;
            } catch (Throwable e) {
                lastException = e;

                // Check if this exception should trigger a retry
                boolean shouldRetry = retryOn.length == 0 || Arrays.stream(retryOn)
                        .anyMatch(type -> type.isInstance(e));

                if (!shouldRetry || attempt == maxAttempts) {
                    if (attempt == maxAttempts) {
                        log.warn("RETRY: {} failed after {}/{} attempts: {}",
                                method, attempt, maxAttempts, e.getMessage());
                    }
                    throw e;
                }

                // Exponential backoff: delayMs * 2^(attempt-1)
                long backoff = delayMs * (1L << (attempt - 1));
                log.warn("RETRY: {} attempt {}/{} failed ({}), retrying in {}ms…",
                        method, attempt, maxAttempts, e.getMessage(), backoff);

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Retry failed unexpectedly");
    }
}
