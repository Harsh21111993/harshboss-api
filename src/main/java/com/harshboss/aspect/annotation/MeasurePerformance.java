package com.harshboss.aspect.annotation;

import java.lang.annotation.*;

/**
 * Measures method execution time and logs a warning if it exceeds the threshold.
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}MeasurePerformance(warnThresholdMs = 5000)
 * public EmailAnalysisDto analyzeEmail(UUID emailId) { ... }
 * </pre>
 *
 * <p>Logs:</p>
 * <pre>
 * PERF: EmailService.analyzeEmail took 3200ms (threshold: 5000ms)
 * PERF WARN: EmailService.analyzeEmail took 8500ms — SLOW (threshold: 5000ms)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MeasurePerformance {
    /** Warn if execution takes longer than this (ms). Default 3000ms. */
    long warnThresholdMs() default 3000L;
}
