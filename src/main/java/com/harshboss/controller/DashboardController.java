package com.harshboss.controller;

import com.harshboss.dto.DailyBriefResponse;
import com.harshboss.dto.StatsResponse;
import com.harshboss.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/stats → StatsResponse
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> stats() {
        return ResponseEntity.ok(dashboardService.stats());
    }

    /**
     * POST /api/dashboard/daily-brief → DailyBriefResponse
     */
    @PostMapping("/daily-brief")
    public ResponseEntity<DailyBriefResponse> dailyBrief() {
        log.info("POST /api/dashboard/daily-brief");
        return ResponseEntity.ok(dashboardService.dailyBrief());
    }
}
