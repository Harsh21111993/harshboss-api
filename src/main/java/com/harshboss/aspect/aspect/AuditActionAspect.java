package com.harshboss.aspect.aspect;

import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.ai.security.AuditLogService;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aspect that implements the {@link AuditAction} annotation.
 *
 * <p>Records the method call to the audit_log table — who called it,
 * when, with what parameters, and the outcome (success/failure).</p>
 *
 * <p>This is the compliance layer — required by ISO 42001 + EU AI Act
 * for tracking AI-related actions.</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditActionAspect {

    private final AuditLogService auditLogService;
    private final UserService userService;

    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        // Build the audit content
        String args = Arrays.stream(joinPoint.getArgs())
                .map(arg -> arg == null ? "null" : truncate(arg.toString(), 100))
                .collect(Collectors.joining(", "));

        String content = "[" + auditAction.action() + "] " + method + "(" + args + ")";

        // Resolve the user (may be null for scheduler-driven calls)
        UUID userId;
        try {
            userId = userService.requireCurrentUserId();
        } catch (Exception e) {
            userId = null;
        }

        // Log the request
        UUID interactionId = auditLogService.logRequest(userId, content, AuditLogService.hash(content));

        // Execute the method
        try {
            Object result = joinPoint.proceed();

            // Log success
            String resultSummary = result != null ? truncate(result.toString(), 200) : "void";
            auditLogService.logResponse(interactionId, userId,
                    "[" + auditAction.action() + "] SUCCESS: " + resultSummary,
                    auditAction.action(), 0, 0, 0, 0.0, 0L, false, null);

            log.debug("AUDIT [{}]: {} → SUCCESS", auditAction.action(), method);
            return result;
        } catch (Throwable e) {
            // Log failure
            auditLogService.logResponse(interactionId, userId,
                    "[" + auditAction.action() + "] FAILED: " + e.getMessage(),
                    auditAction.action(), 0, 0, 0, 0.0, 0L, true, e.getMessage());

            log.warn("AUDIT [{}]: {} → FAILED: {}", auditAction.action(), method, e.getMessage());
            throw e;
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
