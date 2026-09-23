package com.harshboss.aspect.annotation;

import java.lang.annotation.*;

/**
 * Logs method entry, exit, parameters, and return value.
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}LogExecution
 * public List&lt;EmailDto&gt; listEmails(boolean includeSpam) { ... }
 * </pre>
 *
 * <p>Produces log output:</p>
 * <pre>
 * ENTER: EmailService.listEmails(includeSpam=true)
 * EXIT:  EmailService.listEmails → returned 12 items in 45ms
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogExecution {
    /** Log level: "DEBUG" (default) or "INFO". */
    String level() default "DEBUG";

    /** If true, logs method parameters. */
    boolean logArgs() default true;

    /** If true, logs the return value (careful with large objects). */
    boolean logResult() default false;
}
