package com.harshboss.aspect.annotation;

import java.lang.annotation.*;

/**
 * Audits a method — records who called it, when, with what parameters,
 * and the outcome. Persists to the audit_log table.
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}AuditAction(action = "EMAIL_TRIAGE", description = "AI email triage")
 * public EmailAnalysisDto analyzeEmail(UUID emailId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditAction {
    /** The action name (e.g., "EMAIL_TRIAGE", "MEETING_PROPOSE", "APPROVAL_DECIDE"). */
    String action();

    /** Human-readable description of what this action does. */
    String description() default "";
}
