package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.MeasurePerformance;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Aspect that implements the {@link MeasurePerformance} annotation.
 *
 * <p>Measures execution time and logs a WARNING if it exceeds the threshold.
 * Useful for identifying slow LLM calls, DB queries, or sync operations.</p>
 */
@Slf4j
@Aspect
@Component
public class MeasurePerformanceAspect {

    @Around("@annotation(measurePerformance)")
    public Object measure(ProceedingJoinPoint joinPoint, MeasurePerformance measurePerformance) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            if (duration > measurePerformance.warnThresholdMs()) {
                log.warn("PERF WARN: {} took {}ms — SLOW (threshold: {}ms)",
                        method, duration, measurePerformance.warnThresholdMs());
            } else {
                log.debug("PERF: {} took {}ms (threshold: {}ms)",
                        method, duration, measurePerformance.warnThresholdMs());
            }

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("PERF ERROR: {} failed after {}ms: {}", method, duration, e.getMessage());
            throw e;
        }
    }
}
