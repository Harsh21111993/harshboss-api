package com.harshboss.service;

import com.harshboss.entity.CalendarEvent;
import com.harshboss.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects calendar conflicts and finds free slots using a 15-minute grid
 * search across business hours (09:00–18:00, Mon–Fri, UTC).
 *
 * <p>Considers ALL calendar events — including BLOCKED / hidden-by-others slots
 * — because those are exactly the times the user can't book over.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    private final CalendarEventRepository calendarEventRepository;

    /** Business-hours window used by the free-slot grid search. */
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final int BUSINESS_START_HOUR = 9;
    private static final int BUSINESS_END_HOUR   = 18;
    private static final Duration SLOT_STEP      = Duration.ofMinutes(15);

    /**
     * Check whether {@code [start, start+durationMinutes]} overlaps any existing event.
     */
    public ConflictResult check(Instant start, int durationMinutes) {
        Instant end = start.plus(Duration.ofMinutes(Math.max(1, durationMinutes)));
        List<CalendarEvent> events = calendarEventRepository.findAllByOrderByStartTimeAsc();
        for (CalendarEvent ev : events) {
            if (ev.getStartTime() == null || ev.getEndTime() == null) continue;
            // overlap test: not (ev.end <= start || ev.start >= end)
            if (ev.getEndTime().isAfter(start) && ev.getStartTime().isBefore(end)) {
                return new ConflictResult(true, ev.getTitle(), ev.getStartTime(), ev.getEndTime(), ev.isHiddenByOthers());
            }
        }
        return ConflictResult.noConflict();
    }

    /**
     * Find up to {@code count} free slots of length {@code durationMinutes},
     * starting from {@code after}, aligned to 15-minute boundaries within
     * business hours (Mon–Fri 09:00–18:00 UTC). Skips any slot overlapping an
     * existing event.
     */
    public List<Instant> findFreeSlots(Instant after, int durationMinutes, int count) {
        List<Instant> free = new ArrayList<>();
        if (count <= 0) return free;

        int duration = Math.max(15, durationMinutes);
        List<CalendarEvent> events = calendarEventRepository.findAllByOrderByStartTimeAsc();

        ZonedDateTime cursor = ZonedDateTime.ofInstant(
                after == null ? Instant.now() : after,
                ZONE
        ).truncatedTo(ChronoUnit.MINUTES);

        // Align cursor to the next 15-minute boundary
        int minute = cursor.getMinute();
        int mod = minute % 15;
        if (mod != 0) {
            cursor = cursor.plusMinutes(15 - mod);
        }

        int safetyCounter = 0;
        int maxIterations = 4 * 24 * 21; // 15-min slots × 24h × 21 days cap

        while (free.size() < count && safetyCounter++ < maxIterations) {
            // Skip weekends
            if (cursor.getDayOfWeek() == DayOfWeek.SATURDAY || cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
                cursor = cursor.plusDays(1).withHour(BUSINESS_START_HOUR).withMinute(0).withSecond(0).withNano(0);
                continue;
            }
            // Skip outside business hours
            int hour = cursor.getHour();
            Instant slotStart = cursor.toInstant();
            Instant slotEnd = slotStart.plus(Duration.ofMinutes(duration));
            ZonedDateTime slotEndZdt = cursor.plus(Duration.ofMinutes(duration));
            if (hour < BUSINESS_START_HOUR) {
                cursor = cursor.withHour(BUSINESS_START_HOUR).withMinute(0).withSecond(0).withNano(0);
                continue;
            }
            if (slotEndZdt.getHour() > BUSINESS_END_HOUR
                    || (slotEndZdt.getHour() == BUSINESS_END_HOUR && slotEndZdt.getMinute() > 0)
                    || slotEndZdt.getDayOfMonth() != cursor.getDayOfMonth()) {
                // Move to next business day
                cursor = cursor.plusDays(1).withHour(BUSINESS_START_HOUR).withMinute(0).withSecond(0).withNano(0);
                continue;
            }

            boolean overlaps = false;
            for (CalendarEvent ev : events) {
                if (ev.getStartTime() == null || ev.getEndTime() == null) continue;
                if (ev.getEndTime().isAfter(slotStart) && ev.getStartTime().isBefore(slotEnd)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                free.add(slotStart);
            }
            cursor = cursor.plus(SLOT_STEP);
        }

        return free;
    }

    /**
     * Result of a conflict check. {@code conflict=false} means the slot is free.
     */
    public record ConflictResult(
            boolean conflict,
            String eventTitle,
            Instant eventStart,
            Instant eventEnd,
            boolean hiddenByOthers
    ) {
        public static ConflictResult noConflict() {
            return new ConflictResult(false, null, null, null, false);
        }
    }
}
