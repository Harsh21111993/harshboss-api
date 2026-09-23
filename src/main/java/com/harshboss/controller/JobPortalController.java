package com.harshboss.controller;

import com.harshboss.aspect.annotation.RateLimit;
import com.harshboss.entity.JobPortalApplication;
import com.harshboss.service.JobPortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * JobPortalController — centralized job portals hub.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /api/job-portals         → all 7 portals with personalized search URLs</li>
 *   <li>POST /api/job-portals/track    → track a new application</li>
 *   <li>GET  /api/job-portals/tracked  → list tracked applications</li>
 *   <li>POST /api/job-portals/{id}/status → update application status</li>
 *   <li>DELETE /api/job-portals/{id}    → delete a tracked application</li>
 *   <li>GET  /api/job-portals/stats    → stats by portal + status</li>
 *   <li>GET  /api/job-portals/recommendations → AI recommendations</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/job-portals")
@RequiredArgsConstructor
public class JobPortalController {

    private final JobPortalService jobPortalService;

    /** GET /api/job-portals — all 7 portals with personalized search URLs */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getPortals() {
        return ResponseEntity.ok(jobPortalService.getPortalsWithLinks());
    }

    /** POST /api/job-portals/track — track a new job application */
    @PostMapping("/track")
    @RateLimit(maxCalls = 30, windowSeconds = 60)
    public ResponseEntity<JobPortalApplication> track(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(jobPortalService.trackApplication(body));
    }

    /** GET /api/job-portals/tracked — list all tracked applications */
    @GetMapping("/tracked")
    public ResponseEntity<List<JobPortalApplication>> getTracked() {
        return ResponseEntity.ok(jobPortalService.getTrackedApplications());
    }

    /** GET /api/job-portals/tracked/{portal} — applications for a specific portal */
    @GetMapping("/tracked/{portal}")
    public ResponseEntity<List<JobPortalApplication>> getByPortal(@PathVariable String portal) {
        return ResponseEntity.ok(jobPortalService.getApplicationsByPortal(portal));
    }

    /** POST /api/job-portals/{id}/status — update application status */
    @PostMapping("/{id}/status")
    public ResponseEntity<JobPortalApplication> updateStatus(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(jobPortalService.updateStatus(id, body.get("status")));
    }

    /** DELETE /api/job-portals/{id} — delete a tracked application */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        jobPortalService.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/job-portals/stats — stats by portal + status */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(jobPortalService.getStats());
    }

    /** GET /api/job-portals/recommendations — AI-powered portal recommendations */
    @GetMapping("/recommendations")
    public ResponseEntity<List<Map<String, String>>> getRecommendations() {
        return ResponseEntity.ok(jobPortalService.getRecommendations());
    }
}
