package com.harshboss.integration;

import com.harshboss.entity.CalendarEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mock Zoom client.
 *
 * <p>Returns an empty list by default — Zoom meetings appear in the prototype's
 * calendar via the JPA {@code calendar_events} table (seeded with a Zoom demo
 * and a vendor sync). To wire the real Zoom API:</p>
 *
 * <ol>
 *   <li>Create a Server-to-Server OAuth app in the Zoom App Marketplace.</li>
 *   <li>Use the account ID, client ID, and client secret to request an
 *       access token from {@code https://zoom.us/oauth/token}.</li>
 *   <li>Call {@code https://api.zoom.us/v2/users/me/meetings?type=upcoming}
 *       with {@code Authorization: Bearer <token>}.</li>
 *   <li>Map the response to {@link CalendarEvent} entities (use
 *       {@link com.harshboss.entity.enums.Platform#ZOOM}).</li>
 * </ol>
 */
@Slf4j
@Component
public class ZoomClient {

    public List<CalendarEvent> fetchEvents(Instant from, Instant to) {
        log.debug("ZoomClient.fetchEvents({} → {}): mock — returning 0 events", from, to);
        return List.of();
    }
}
