package com.harshboss.jobs.controller;

import com.harshboss.aspect.annotation.RateLimit;
import com.harshboss.entity.JobPortalApplication;
import com.harshboss.jobs.service.JobAggregatorService;
import com.harshboss.service.JobPortalService;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JobAggregatorController — the main skill-matched job search endpoint + bookmarklet.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /api/jobs-aggregator/search — aggregated + skill-filtered jobs</li>
 *   <li>GET  /api/jobs-aggregator/bookmarklet — generates the browser bookmarklet</li>
 *   <li>POST /api/jobs-aggregator/capture — receives a job captured by the bookmarklet</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs-aggregator")
@RequiredArgsConstructor
public class JobAggregatorController {

    private final JobAggregatorService aggregatorService;
    private final JobPortalService portalService;
    private final UserService userService;

    /**
     * POST /api/jobs-aggregator/search?maxResults=10
     * Searches SerpAPI + Google CSE + JSearch, deduplicates, filters by skills.
     */
    @PostMapping("/search")
    @RateLimit(maxCalls = 10, windowSeconds = 60)
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(defaultValue = "10") int maxResults) {
        return ResponseEntity.ok(aggregatorService.searchSkillMatchedJobs(maxResults));
    }

    /**
     * GET /api/jobs-aggregator/bookmarklet
     * Returns a JavaScript bookmarklet that extracts schema.org/JobPosting data
     * from any of the 7 portal pages and sends it to Harsh-Boss.
     */
    @GetMapping(value = "/bookmarklet", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getBookmarklet() {
        // The bookmarklet extracts JobPosting JSON-LD from the current page
        // and POSTs it to our /capture endpoint
        String bookmarklet = """
            javascript:(function(){
              const scripts = Array.from(document.querySelectorAll('script[type="application/ld+json"]'));
              let jobData = null;
              for (const script of scripts) {
                try {
                  const json = JSON.parse(script.innerText);
                  const items = Array.isArray(json) ? json : (json['@graph'] || [json]);
                  const found = items.find(item => item['@type'] === 'JobPosting');
                  if (found) { jobData = found; break; }
                } catch(e) {}
              }
              const payload = {
                title: jobData ? (jobData.title || document.title) : document.title,
                company: jobData && jobData.hiringOrganization ? jobData.hiringOrganization.name : 'Unknown',
                location: jobData && jobData.jobLocation ? (jobData.jobLocation.address ? jobData.jobLocation.address.addressLocality : 'Unknown') : 'Unknown',
                salary: jobData && jobData.baseSalary ? JSON.stringify(jobData.baseSalary) : '',
                jobUrl: window.location.href,
                portal: detectPortal(window.location.href),
                source: 'bookmarklet'
              };
              function detectPortal(url) {
                const u = url.toLowerCase();
                if (u.includes('naukri')) return 'NAUKRI';
                if (u.includes('linkedin')) return 'LINKEDIN';
                if (u.includes('wellfound')) return 'WELLFOUND';
                if (u.includes('instahyre')) return 'INSTAHYRE';
                if (u.includes('otta') || u.includes('welcometothejungle')) return 'OTTA';
                if (u.includes('turing')) return 'TURING';
                if (u.includes('cutshort')) return 'CUTSHORT';
                return 'OTHER';
              }
              fetch('http://localhost:8080/api/jobs-aggregator/capture', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
              }).then(r => r.json()).then(d => {
                alert('✅ Job captured in Harsh-Boss!\\\\n' + payload.title + ' at ' + payload.company);
              }).catch(e => {
                alert('❌ Could not capture. Make sure Harsh-Boss is running on :8080');
              });
            })();
            """;

        // Return as an HTML page with install instructions
        String html = """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Harsh-Boss Bookmarklet</title>
            <style>body{font-family:system-ui,sans-serif;max-width:600px;margin:40px auto;padding:20px}
            .bookmarklet{display:inline-block;padding:12px 24px;background:#059669;color:white;
            text-decoration:none;border-radius:8px;font-weight:bold;font-size:16px;margin:20px 0}
            .note{background:#fef3c7;padding:12px;border-radius:8px;margin:16px 0}</style></head>
            <body><h1>📋 Harsh-Boss Job Capture Bookmarklet</h1>
            <p>Drag this button to your bookmarks bar:</p>
            <a href="%s" class="bookmarklet" draggable="true">📋 Capture Job for Harsh-Boss</a>
            <div class="note">⚠️ When viewing a job on Naukri/LinkedIn/Wellfound/etc.,
            click the bookmarklet to capture it into Harsh-Boss.</div>
            <p><b>How it works:</b></p>
            <ol><li>Drag the button above to your browser's bookmarks bar</li>
            <li>Browse any of the 7 job portals</li>
            <li>When you find a job you like, click the bookmarklet</li>
            <li>The job is automatically captured in Harsh-Boss with title, company, and URL</li>
            <li>Harsh-Boss checks if it matches your skills and adds it to tracking</li></ol>
            </body></html>
            """.formatted(bookmarklet.replace("\n", "").replace("\r", ""));

        return ResponseEntity.ok(html);
    }

    /**
     * POST /api/jobs-aggregator/capture
     * Receives a job captured by the bookmarklet and adds it to tracking.
     */
    @PostMapping("/capture")
    @RateLimit(maxCalls = 30, windowSeconds = 60)
    public ResponseEntity<Map<String, Object>> capture(@RequestBody Map<String, String> body) {
        log.info("Bookmarklet capture: {} at {}", body.get("title"), body.get("company"));

        // Track the captured job
        JobPortalApplication app = portalService.trackApplication(body);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Job captured: " + body.get("title") + " at " + body.get("company"),
                "applicationId", app.getId().toString()
        ));
    }
}
