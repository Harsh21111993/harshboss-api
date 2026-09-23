package com.harshboss.integration;

import com.harshboss.entity.CalendarEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mock Google Calendar client.
 *
 * <p>Returns an empty list by default so the prototype's calendar is driven by
 * the JPA {@code calendar_events} table (which the V2 seed populates with a
 * few Google events). To wire the real Google Calendar API:</p>
 *
 * <ol>
 *   <li>Create an OAuth2 credential in Google Cloud Console (type: Web application).</li>
 *   <li>Add {@code com.google.apis:google-api-services-calendar} and
 *       {@code com.google.auth:google-auth-library-oauth2-http} to {@code pom.xml}.</li>
 *   <li>Use the {@code calendar.events().list("primary").setTimeMin(...).setTimeMax(...).execute()}
 *       flow with an authorized {@code Credential}.</li>
 *   <li>Map the response to {@link CalendarEvent} entities and persist them.</li>
 * </ol>
 *
 * <p>Auth Tip: For server-to-server (no user), use a Google Service Account
 * with domain-wide delegation.</p>
 */
@Slf4j
@Component
public class GoogleCalendarClient {

    public List<CalendarEvent> fetchEvents(Instant from, Instant to) {
        log.debug("GoogleCalendarClient.fetchEvents({} → {}): mock — returning 0 events", from, to);
        return List.of();
    }
}
