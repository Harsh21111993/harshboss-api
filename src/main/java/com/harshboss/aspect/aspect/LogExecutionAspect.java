package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.LogExecution;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Aspect that implements the {@link LogExecution} annotation.
 *
 * <p>Logs method entry (with parameters) and exit (with duration + return value).
 * Configurable via the annotation's attributes: level, logArgs, logResult.</p>
 */
@Slf4j
@Aspect
@Component
public class LogExecutionAspect {

    @Around("@annotation(logExecution)")
    public Object logMethod(ProceedingJoinPoint joinPoint, LogExecution logExecution) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // Log entry
        String entryMsg;
        if (logExecution.logArgs()) {
            String args = Arrays.stream(joinPoint.getArgs())
                    .map(arg -> arg == null ? "null" : truncate(arg.toString(), 100))
                    .collect(Collectors.joining(", "));
            entryMsg = "ENTER: {}.{}({})", className, methodName, args;
        } else {
            entryMsg = "ENTER: {}.{}()", className, methodName;
        }

        if ("INFO".equalsIgnoreCase(logExecution.level())) {
            log.info(entryMsg);
        } else {
            log.debug(entryMsg);
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            // Log exit
            String resultStr = "";
            if (logExecution.logResult() && result != null) {
                resultStr = " → " + truncate(result.toString(), 200);
            }

            String exitMsg = "EXIT:  {}.{} took {}ms{}", className, methodName, duration, resultStr;
            if ("INFO".equalsIgnoreCase(logExecution.level())) {
                log.info(exitMsg);
            } else {
                log.debug(exitMsg);
            }

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("ERROR: {}.{} threw {} after {}ms: {}",
                    className, methodName, e.getClass().getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
