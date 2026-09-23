package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aspect that implements the {@link RateLimit} annotation.
 *
 * <p>Tracks calls per method per time window. If the limit is exceeded,
 * throws a 429 Too Many Requests HTTP error.</p>
 *
 * <p>Thread-safe — uses ConcurrentHashMap with AtomicInteger counters.</p>
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** Key: method name, Value: { windowStart, callCount } */
    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodKey = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        long now = System.currentTimeMillis();
        long windowMs = rateLimit.windowSeconds() * 1000L;

        RateWindow window = windows.compute(methodKey, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMs) {
                // New window
                return new RateWindow(now, new AtomicInteger(1));
            }
            // Existing window — increment
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > rateLimit.maxCalls()) {
            log.warn("RATE LIMIT exceeded: {} — {} calls in {}s (max: {})",
                    methodKey, window.count.get(), rateLimit.windowSeconds(), rateLimit.maxCalls());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded: max " + rateLimit.maxCalls() +
                    " calls per " + rateLimit.windowSeconds() + " seconds for " + methodKey);
        }

        log.debug("RATE LIMIT: {} — {}/{} calls in current window",
                methodKey, window.count.get(), rateLimit.maxCalls());
        return joinPoint.proceed();
    }

    private record RateWindow(long windowStart, AtomicInteger count) {}
}
