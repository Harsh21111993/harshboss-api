package com.harshboss.integration;

import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.enums.EventStatus;
import com.harshboss.entity.enums.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mock Microsoft Graph client for Teams + Outlook calendar events.
 *
 * <p>Returns an empty list by default so the prototype's calendar is driven by
 * the JPA {@code calendar_events} table (which the V2 seed populates with a
 * few Teams events). To wire the real Microsoft Graph API:</p>
 *
 * <ol>
 *   <li>Register an Azure AD application with application permissions
 *       {@code Calendars.Read} and {@code Mail.Read} (or delegated permissions
 *       for a per-user flow).</li>
 *   <li>Add MSAL4J ({@code com.microsoft.azure:msal4j}) to {@code pom.xml}.</li>
 *   <li>Acquire an OAuth2 token via
 *       {@code ConfidentialClientApplication.acquireTokenForClient(...)}.</li>
 *   <li>Call {@code https://graph.microsoft.com/v1.0/me/calendarview?startDateTime=...&endDateTime=...}
 *       with {@code Authorization: Bearer <token>} using Spring's
 *       {@link org.springframework.web.reactive.function.client.WebClient}.</li>
 *   <li>Map the JSON response to {@link CalendarEvent} entities and persist
 *       them so conflict detection sees them.</li>
 * </ol>
 *
 * <p>Auth Tip: Use the Spring Boot starter
 * {@code spring-boot-starter-oauth2-client} with the {@code client_credentials}
 * flow for service-to-service calls.</p>
 */
@Slf4j
@Component
public class MicrosoftGraphClient {

    /**
     * Fetch the user's Teams/Outlook events in the given window.
     *
     * @param from window start (inclusive)
     * @param to   window end (exclusive)
     * @return events; empty list in this mock
     */
    public List<CalendarEvent> fetchEvents(Instant from, Instant to) {
        log.debug("MicrosoftGraphClient.fetchEvents({} → {}): mock — returning 0 events", from, to);
        return List.of();
    }
}
