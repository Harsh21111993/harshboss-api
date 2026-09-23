package com.harshboss.controller;

import com.harshboss.dto.UserDto;
import com.harshboss.service.DemoDataService;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin / setup endpoints.
 *
 * <p>POST /api/admin/seed-demo → seeds 12 demo emails + 12 calendar events +
 * 1 pending approval for the current user. Idempotent (no-op if data exists).
 * Triggered by the "Load demo data" button on the dashboard.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DemoDataService demoDataService;
    private final UserService userService;

    @PostMapping("/seed-demo")
    public ResponseEntity<Map<String, Object>> seedDemo() {
        log.info("POST /api/admin/seed-demo");
        DemoDataService.SeedResult result = demoDataService.seedDemoData();
        UserDto user = userService.getCurrentUser();
        return ResponseEntity.ok(Map.of(
                "created", result.created(),
                "emails", result.emails(),
                "events", result.events(),
                "approvals", result.approvals(),
                "user", user.fullName(),
                "message", result.created()
                        ? "Demo data loaded: " + result.emails() + " emails, "
                          + result.events() + " events, " + result.approvals() + " approval."
                        : "You already have data (" + result.emails() + " emails, "
                          + result.events() + " events). Nothing to seed."
        ));
    }
}
