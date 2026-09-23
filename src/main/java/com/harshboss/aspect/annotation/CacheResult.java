package com.harshboss.aspect.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Caches the method's return value in memory for a specified TTL.
 * Useful for expensive operations (LLM calls, DB aggregations).
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}CacheResult(ttl = 30, unit = TimeUnit.MINUTES)
 * public StatsResponse stats() { ... }
 * </pre>
 *
 * <p>The cache key is derived from the method name + parameters.
 * If the same parameters are passed within the TTL, the cached result
 * is returned without executing the method.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheResult {
    /** Time-to-live for the cached value. */
    long ttl() default 5;

    /** Time unit for the TTL. Default minutes. */
    TimeUnit unit() default TimeUnit.MINUTES;

    /** Maximum number of entries in the cache. Default 100. */
    int maxSize() default 100;
}
