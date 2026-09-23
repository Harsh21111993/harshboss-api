package com.harshboss.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Placeholder scheduler for inbound email sync.
 *
 * <p>In the prototype this just logs a tick every 60s — the seed data is static.
 * To wire real sync, replace the body of {@link #syncEmails()} with a call to
 * Microsoft Graph / Gmail's "list messages (delta)" API, persist new emails to
 * the {@code emails} table, and (optionally) trigger AI triage on each new
 * arrival.</p>
 *
 * <p>EnableScheduling is on this class so it's obvious where the scheduling
 * infrastructure is mounted; you can also move it to a dedicated
 * {@code @Configuration}.</p>
 */
@Slf4j
@Component
@EnableScheduling
public class EmailSyncScheduler {

    /**
     * Run once at startup so the first sync is visible in the log without
     * waiting for the fixed-delay tick.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Email sync scheduler initialized — polling every 60s");
    }

    /**
     * Fixed-delay poll (60s). Triggers after the previous run completes.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void syncEmails() {
        // TODO: replace with real MS Graph / Gmail delta sync.
        log.debug("Email sync tick — no-op (prototype uses seeded data)");
    }
}
