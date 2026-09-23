package com.harshboss.repository;

import com.harshboss.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    List<CalendarEvent> findByStartTimeBetweenOrderByStartTimeAsc(Instant from, Instant to);

    List<CalendarEvent> findAllByOrderByStartTimeAsc();

    // ── User-scoped queries ──
    List<CalendarEvent> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(UUID userId, Instant from, Instant to);

    List<CalendarEvent> findByUserIdOrderByStartTimeAsc(UUID userId);

    long countByUserId(UUID userId);

    /** Check if an event already exists for a given source email (dedup). */
    Optional<CalendarEvent> findBySourceEmailId(UUID sourceEmailId);
}
