package com.harshboss.controller;

import com.harshboss.dto.CalendarEventDto;
import com.harshboss.dto.ProposeMeetingRequest;
import com.harshboss.dto.ProposeMeetingResponse;
import com.harshboss.service.CalendarService;
import com.harshboss.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final ApprovalService approvalService;

    /**
     * GET /api/calendar/events?from=&to= → CalendarEventDto[]
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> events(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to",   required = false) Instant to) {
        return ResponseEntity.ok(calendarService.getUnifiedEvents(from, to));
    }

    /**
     * POST /api/calendar/propose → ProposeMeetingResponse
     */
    @PostMapping("/propose")
    public ResponseEntity<ProposeMeetingResponse> propose(@Valid @RequestBody ProposeMeetingRequest req) {
        log.info("POST /api/calendar/propose — title='{}' start={} duration={}min platform={}",
                req.title(), req.proposedStart(), req.durationMinutes(), req.platform());
        ProposeMeetingResponse response = approvalService.createFromProposal(req);
        return ResponseEntity.ok(response);
    }
}
