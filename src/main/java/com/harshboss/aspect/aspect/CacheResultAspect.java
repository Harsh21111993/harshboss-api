package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.CacheResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Aspect that implements the {@link CacheResult} annotation.
 *
 * <p>Caches the method's return value in memory for a specified TTL.
 * The cache key is derived from the method name + parameters (toString).</p>
 *
 * <p>Uses a simple ConcurrentHashMap with TTL-based eviction.
 * For production, replace with Caffeine or Redis.</p>
 */
@Slf4j
@Aspect
@Component
public class CacheResultAspect {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Around("@annotation(cacheResult)")
    public Object cache(ProceedingJoinPoint joinPoint, CacheResult cacheResult) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodKey = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        // Build cache key from method + args
        String argsKey = Arrays.stream(joinPoint.getArgs())
                .map(arg -> arg == null ? "null" : arg.toString())
                .reduce("", (a, b) -> a + "|" + b);
        String cacheKey = methodKey + ":" + argsKey.hashCode();

        long ttlMs = TimeUnit.MILLISECONDS.convert(cacheResult.ttl(), cacheResult.unit());
        long now = System.currentTimeMillis();

        // Check if we have a valid cached entry
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && (now - entry.timestamp) < ttlMs) {
            log.debug("CACHE HIT: {} (age: {}ms, ttl: {}ms)", methodKey, now - entry.timestamp, ttlMs);
            return entry.value;
        }

        // Cache miss — execute the method
        log.debug("CACHE MISS: {} — executing method", methodKey);
        Object result = joinPoint.proceed();

        // Store in cache
        cache.put(cacheKey, new CacheEntry(result, now));

        // Evict old entries if cache is too large
        if (cache.size() > cacheResult.maxSize()) {
            cache.entrySet().stream()
                    .filter(e -> (now - e.getValue().timestamp) > ttlMs)
                    .map(Map.Entry::getKey)
                    .forEach(cache::remove);
        }

        return result;
    }

    private record CacheEntry(Object value, long timestamp) {}
}
