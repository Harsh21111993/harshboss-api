package com.harshboss.service;

import com.harshboss.dto.CalendarEventDto;
import com.harshboss.dto.DtoMapper;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.enums.EventStatus;
import com.harshboss.entity.enums.Platform;
import com.harshboss.integration.GoogleCalendarClient;
import com.harshboss.integration.MicrosoftGraphClient;
import com.harshboss.integration.ZoomClient;
import com.harshboss.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Unified calendar: merges events from the JPA {@code calendar_events} table
 * (the source of truth for the prototype) with mock platform clients.
 *
 * <p>In production, the integration clients would fan out to MS Graph / Google
 * Calendar / Zoom in parallel and the JPA table would only hold personal +
 * tentative events. The shape of this service doesn't change — only the
 * underlying client implementations do.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final MicrosoftGraphClient microsoftGraphClient;
    private final GoogleCalendarClient googleCalendarClient;
    private final ZoomClient zoomClient;
    private final UserService userService;
    private final DtoMapper dtoMapper;

    /**
     * Get all calendar events in {@code [from, to]} across every platform,
     * sorted ascending by start time.
     */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> getUnifiedEvents(Instant from, Instant to) {
        Instant effectiveFrom = from == null ? Instant.now().minus(java.time.Duration.ofDays(1)) : from;
        Instant effectiveTo   = to   == null ? Instant.now().plus(java.time.Duration.ofDays(7))  : to;

        List<CalendarEvent> all = new ArrayList<>();

        // 1. Personal / seeded events from the database, scoped to the current user.
        UUID userId = userService.requireCurrentUserId();
        all.addAll(calendarEventRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, effectiveFrom, effectiveTo));

        // 2. Platform integrations (mock in the prototype — return empty lists).
        try { all.addAll(microsoftGraphClient.fetchEvents(effectiveFrom, effectiveTo)); }
        catch (Exception e) { log.warn("MS Graph fetch failed: {}", e.getMessage()); }
        try { all.addAll(googleCalendarClient.fetchEvents(effectiveFrom, effectiveTo)); }
        catch (Exception e) { log.warn("Google Calendar fetch failed: {}", e.getMessage()); }
        try { all.addAll(zoomClient.fetchEvents(effectiveFrom, effectiveTo)); }
        catch (Exception e) { log.warn("Zoom fetch failed: {}", e.getMessage()); }

        // Sort by start time; dedup is unnecessary in the prototype (mocks return empty).
        all.sort(Comparator.comparing(CalendarEvent::getStartTime));
        return dtoMapper.toEventDtos(all);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventDto> getAllEvents() {
        UUID userId = userService.requireCurrentUserId();
        return dtoMapper.toEventDtos(calendarEventRepository.findByUserIdOrderByStartTimeAsc(userId));
    }

    /**
     * Count events whose start_time falls within the calendar day of {@code day}
     * (UTC). Used by the dashboard "meetings today" stat.
     */
    @Transactional(readOnly = true)
    public long countEventsOnDay(Instant day) {
        UUID userId = userService.requireCurrentUserId();
        ZonedDateTime midnight = day.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        Instant startOfDay = midnight.toInstant();
        Instant endOfDay   = midnight.plusDays(1).toInstant();
        return calendarEventRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, startOfDay, endOfDay).size();
    }

    /**
     * Persist a new event (used when a free proposal is created — tentative —
     * and when an approval is approved — promoted to CONFIRMED).
     */
    @Transactional
    public CalendarEvent createEvent(String title, Platform platform, Instant start, Instant end,
                                     String organizer, List<String> attendees, String joinUrl,
                                     String location, boolean hiddenByOthers, EventStatus status) {
        return createEvent(title, platform, start, end, organizer, attendees,
                joinUrl, location, hiddenByOthers, status, null);
    }

    /**
     * Persist a new event with an optional sourceEmailId (for events auto-created
     * from meeting invitation emails).
     */
    @Transactional
    public CalendarEvent createEvent(String title, Platform platform, Instant start, Instant end,
                                     String organizer, List<String> attendees, String joinUrl,
                                     String location, boolean hiddenByOthers, EventStatus status,
                                     java.util.UUID sourceEmailId) {
        CalendarEvent ev = new CalendarEvent();
        ev.setTitle(title);
        ev.setPlatform(platform);
        ev.setStartTime(start);
        ev.setEndTime(end);
        ev.setOrganizer(organizer);
        ev.setAttendees(com.harshboss.dto.JsonUtil.toJson(attendees));
        ev.setJoinUrl(joinUrl);
        ev.setLocation(location);
        ev.setHiddenByOthers(hiddenByOthers);
        ev.setStatus(status);
        ev.setSourceEmailId(sourceEmailId);
        ev.setUserId(userService.requireCurrentUserId());
        return calendarEventRepository.save(ev);
    }
}
